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

/**
 * Sync metadata for setting keys, held as sibling keys in the same [Settings] store a setting's
 * value lives in. A setting has no row of its own the way a node or edge does, so this is the
 * closest equivalent: one [stamp] call per [ConfigItem] write.
 */
class SettingSyncMetadataStore(
  private val settings: Settings,
  private val deviceIdentity: DeviceIdentity,
) {

  // Guards the whole stamp critical section (sequence allocation plus all four writes), not just
  // the writes: without it, two concurrent stamp() calls for the same key can allocate seq 1 and
  // seq 2, then race to write their four fields, and the seq-1 writer finishing last would leave
  // stale metadata behind even though seq-2 is what is actually stored. A non-suspend spinlock
  // (Mutex.tryLock/unlock) so stamp can be called inline from the non-suspend
  // proj.memorchess.axl.core.config.ConfigItem.setValue and reset.
  private val lock = Mutex()

  /**
   * Stamps [key] as written just now by this device, marking it deleted when [isDeleted].
   *
   * Sequence allocation and all four writes happen under one lock, so a concurrent stamp of the
   * same key can never finish out of order and leave stale metadata behind. The four fields are
   * written with `updatedAt` last: [read] gates on `updatedAt` alone, so a crash mid-stamp leaves a
   * reader seeing the previous, fully consistent value instead of a half-updated one.
   *
   * @return The `deviceSeq` this call allocated, so a caller that also needs to enqueue an outbox
   *   entry (see [proj.memorchess.axl.core.data.DatabaseQueryManager.markDirty]) stamps and queues
   *   at the same sequence without allocating a second one.
   */
  fun stamp(key: String, isDeleted: Boolean = false): Long {
    while (!lock.tryLock()) {
      /* spin: the critical section is one deviceSeq allocation plus four Settings writes */
    }
    try {
      val seq = deviceIdentity.nextDeviceSeq()
      settings.putBoolean(isDeletedKey(key), isDeleted)
      settings.putString(originDeviceKey(key), deviceIdentity.originDevice)
      settings.putLong(deviceSeqKey(key), seq)
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
