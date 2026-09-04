package proj.memorchess.axl.core.sync

import com.russhwolf.settings.Settings
import kotlin.uuid.Uuid
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Stable per install identifier plus a monotonic write counter, the two fields every synced row
 * uses to break same author conflicts (see [proj.memorchess.axl.core.data.DirtyKey] and the sync
 * design doc, section 5.4).
 *
 * [originDevice] survives a logout and is only regenerated when local data is erased, because it
 * identifies the install, not the account. [nextDeviceSeq] persists on every call so the counter
 * cannot go backwards across a restart, which is what makes it safe to compare without a clock.
 */
class DeviceIdentity private constructor(private val settings: Settings?) {

  /** Opaque id of this install. Never displayed, never an identity. */
  val originDevice: String =
    settings?.let { s ->
      s.getStringOrNull(KEY_ORIGIN_DEVICE) ?: generateId().also { s.putString(KEY_ORIGIN_DEVICE, it) }
    } ?: generateId()

  private val mutex = Mutex()
  private var seq: Long = settings?.getLong(KEY_DEVICE_SEQ, 0L) ?: 0L

  /** Allocates the next strictly increasing sequence number for a write made by this device. */
  suspend fun nextDeviceSeq(): Long =
    mutex.withLock {
      seq += 1
      settings?.putLong(KEY_DEVICE_SEQ, seq)
      seq
    }

  companion object {
    private const val KEY_ORIGIN_DEVICE = "sync.originDevice"
    private const val KEY_DEVICE_SEQ = "sync.deviceSeq"

    /** Backed by [settings], surviving process restarts. Use for the real, process wide identity. */
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
