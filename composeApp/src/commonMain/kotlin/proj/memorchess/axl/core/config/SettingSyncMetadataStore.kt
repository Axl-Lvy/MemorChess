package proj.memorchess.axl.core.config

import com.russhwolf.settings.Settings
import kotlin.time.Instant
import kotlinx.coroutines.sync.Mutex
import proj.memorchess.axl.core.date.DateUtil
import proj.memorchess.axl.core.sync.DeviceIdentity

/**
 * The four sync fields for one setting key, mirroring [proj.memorchess.axl.core.data.DataNode]'s.
 */
internal data class SettingSyncMetadata(
  val isDeleted: Boolean,
  val updatedAt: Instant,
  val originDevice: String,
  val deviceSeq: Long,
)

/** Sync metadata for setting keys, stored as sibling keys in the same [Settings] store. */
class SettingSyncMetadataStore(
  private val settings: Settings,
  private val deviceIdentity: DeviceIdentity,
) {

  // Covers seq allocation and all four writes, so an older stamp cannot finish last and leave stale
  // metadata. Spun with tryLock because stamp runs from non-suspend ConfigItem.setValue and reset.
  private val lock = Mutex()

  /** Stamps [key] as written now by this device and returns the `deviceSeq` it allocated. */
  fun stamp(key: String, isDeleted: Boolean = false): Long {
    while (!lock.tryLock()) {
      /* spin: the critical section is short */
    }
    try {
      val seq = deviceIdentity.nextDeviceSeq()
      settings.putBoolean(isDeletedKey(key), isDeleted)
      settings.putString(originDeviceKey(key), deviceIdentity.originDevice.value)
      settings.putLong(deviceSeqKey(key), seq)
      // Last: read gates on updatedAt, so a crash mid-stamp leaves the previous metadata readable.
      settings.putLong(updatedAtKey(key), DateUtil.now().epochSeconds)
      return seq
    } finally {
      lock.unlock()
    }
  }

  /** Reads [key]'s sync metadata, or `null` when it has never been [stamp]ed. */
  internal fun read(key: String): SettingSyncMetadata? {
    val updatedAtSeconds = settings.getLongOrNull(updatedAtKey(key)) ?: return null
    return SettingSyncMetadata(
      isDeleted = settings.getBoolean(isDeletedKey(key), false),
      updatedAt = Instant.fromEpochSeconds(updatedAtSeconds),
      originDevice = settings.getString(originDeviceKey(key), ""),
      deviceSeq = settings.getLong(deviceSeqKey(key), 0L),
    )
  }

  private fun isDeletedKey(key: String) = "$key.sync.isDeleted"

  private fun updatedAtKey(key: String) = "$key.sync.updatedAt"

  private fun originDeviceKey(key: String) = "$key.sync.originDevice"

  private fun deviceSeqKey(key: String) = "$key.sync.deviceSeq"
}
