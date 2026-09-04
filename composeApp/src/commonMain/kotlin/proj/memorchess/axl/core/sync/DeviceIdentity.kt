package proj.memorchess.axl.core.sync

import com.russhwolf.settings.Settings
import kotlin.uuid.Uuid
import kotlinx.coroutines.sync.Mutex

/**
 * Stable per install identifier plus a monotonic write counter, the two fields every synced row
 * uses to break same author conflicts (see [proj.memorchess.axl.core.data.DirtyKey] and the sync
 * design doc, section 5.4).
 *
 * [originDevice] survives a logout and is only regenerated when local data is erased, because it
 * identifies the install, not the account. [nextDeviceSeq] hands out numbers from a block already
 * persisted ahead of use, so an ungraceful kill can strand at most one block's worth of unused
 * numbers rather than reusing one already handed out; see [nextDeviceSeq] for the durability
 * guarantee this actually gives.
 */
class DeviceIdentity private constructor(private val settings: Settings?) {

  /** Opaque id of this install. Never displayed, never an identity. */
  val originDevice: String =
    settings?.let { s ->
      s.getStringOrNull(KEY_ORIGIN_DEVICE)
        ?: generateId().also { s.putString(KEY_ORIGIN_DEVICE, it) }
    } ?: generateId()

  // A non-suspend spinlock (Mutex.tryLock/unlock, not the suspend lock()/withLock() API) so
  // nextDeviceSeq can be called from non-suspend call sites such as
  // proj.memorchess.axl.core.config.ConfigItem.setValue. The critical section is a handful of var
  // reads/writes and, at most once per block, one Settings write, so busy-waiting on contention is
  // cheap.
  private val lock = Mutex()
  private var seq: Long = settings?.getLong(KEY_DEVICE_SEQ, 0L) ?: 0L
  private var reservedUpTo: Long = seq

  /**
   * Allocates the next strictly increasing sequence number for a write made by this device.
   *
   * Durable in blocks of [RESERVATION_BLOCK_SIZE]: the upper bound of the current block is
   * persisted before any number in it is handed out, so a number is only ever handed out once that
   * boundary is safely on disk. An ungraceful kill can therefore only strand the unused tail of the
   * current block (at most [RESERVATION_BLOCK_SIZE] numbers per restart) never behind, unlike
   * persisting after every call, where the same kill can resume from a stale value and reuse a
   * number already handed out.
   */
  fun nextDeviceSeq(): Long {
    while (!lock.tryLock()) {
      /* spin: the critical section below is a few var operations plus at most one Settings write */
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

    /**
     * Backed by [settings], surviving process restarts. Use for the real, process wide identity.
     */
    fun persisted(settings: Settings): DeviceIdentity = DeviceIdentity(settings)

    /**
     * Never persisted: a fresh id and a counter starting at zero every time. For throwaway stores
     * ([proj.memorchess.axl.core.interactions.RepertoireExplorer]) and tests that do not care about
     * cross session identity.
     */
    fun ephemeral(): DeviceIdentity = DeviceIdentity(null)

    private fun generateId(): String = Uuid.random().toString()
  }
}
