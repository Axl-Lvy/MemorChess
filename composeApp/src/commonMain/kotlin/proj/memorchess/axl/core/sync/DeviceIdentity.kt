package proj.memorchess.axl.core.sync

import com.russhwolf.settings.Settings
import kotlinx.coroutines.sync.Mutex

/**
 * Stable per install id plus a monotonic write counter, used to break same author sync conflicts.
 * [originDevice] identifies the install, not the account, so it survives a logout.
 */
class DeviceIdentity private constructor(private val settings: Settings?) {

  /** Opaque id of this install. Never displayed, never an identity. */
  val originDevice: DeviceId =
    settings?.let { s ->
      s.getStringOrNull(KEY_ORIGIN_DEVICE)?.let(DeviceId::parseOrNull)
        ?: DeviceId.random().also { s.putString(KEY_ORIGIN_DEVICE, it.value) }
    } ?: DeviceId.random()

  // Spun on with tryLock so nextDeviceSeq stays non-suspend; the critical section is tiny.
  private val lock = Mutex()
  private var seq: Long = settings?.getLong(KEY_DEVICE_SEQ, 0L) ?: 0L
  private var reservedUpTo: Long = seq

  /**
   * Allocates the next strictly increasing sequence number for a write by this device. Each block's
   * upper bound is persisted before use, so a kill may skip numbers but never reuses one.
   */
  fun nextDeviceSeq(): Long {
    while (!lock.tryLock()) {
      /* spin */
    }
    try {
      if (seq >= reservedUpTo) {
        reservedUpTo += RESERVATION_BLOCK_SIZE
        settings?.putLong(KEY_DEVICE_SEQ, reservedUpTo)
      }
      seq += 1
      return seq
    } finally {
      lock.unlock()
    }
  }

  companion object {
    private const val KEY_ORIGIN_DEVICE = "sync.originDevice"
    private const val KEY_DEVICE_SEQ = "sync.deviceSeq"

    /** Size of one durable reservation block. See [nextDeviceSeq]. */
    private const val RESERVATION_BLOCK_SIZE = 1000L

    /** Backed by [settings], surviving process restarts. */
    fun persisted(settings: Settings): DeviceIdentity = DeviceIdentity(settings)

    /** Never persisted: a fresh id and a counter starting at zero every time. */
    fun ephemeral(): DeviceIdentity = DeviceIdentity(null)
  }
}
