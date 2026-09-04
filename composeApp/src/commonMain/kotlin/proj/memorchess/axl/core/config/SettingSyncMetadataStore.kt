package proj.memorchess.axl.core.config

import com.russhwolf.settings.Settings
import kotlin.time.Instant
import proj.memorchess.axl.core.date.DateUtil
import proj.memorchess.axl.core.sync.DeviceIdentity

/**
 * The four sync fields for one setting key, mirroring [proj.memorchess.axl.core.data.DataNode]'s.
 */
data class SettingSyncMetadata(
  val isDeleted: Boolean,
  val updatedAt: Instant,
  val originDevice: String,
  val deviceSeq: Long,
)

/**
 * Sync metadata for setting keys, held as sibling keys in the same [Settings] store a setting's
 * value lives in. A setting has no row of its own the way a node or edge does, so this is the
 * closest equivalent: one [stamp] call per [ConfigItem] write.
 */
class SettingSyncMetadataStore(
  private val settings: Settings,
  private val deviceIdentity: DeviceIdentity,
) {

  /** Stamps [key] as written just now by this device, marking it deleted when [isDeleted]. */
  suspend fun stamp(key: String, isDeleted: Boolean = false) {
    val seq = deviceIdentity.nextDeviceSeq()
    settings.putBoolean(isDeletedKey(key), isDeleted)
    settings.putLong(updatedAtKey(key), DateUtil.now().epochSeconds)
    settings.putString(originDeviceKey(key), deviceIdentity.originDevice)
    settings.putLong(deviceSeqKey(key), seq)
  }

  /** Reads [key]'s sync metadata, or `null` when it has never been [stamp]ed. */
  fun read(key: String): SettingSyncMetadata? {
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
