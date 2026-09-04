package proj.memorchess.axl.core.data

import androidx.room.Entity

/**
 * One outbox row: a dirty [DirtyKey], not a copy of the row it names. See [DirtyKey] for why.
 *
 * @property kind One of [KIND_NODE], [KIND_EDGE], [KIND_SETTING].
 * @property key1 The position key, the edge's origin, or the setting key.
 * @property key2 The edge's destination, empty for the other two kinds.
 * @property deviceSeq The `deviceSeq` of the write this entry names. See [OutboxEntry] and
 *   [DatabaseQueryManager.clearDirty].
 */
@Entity(tableName = "OutboxEntryEntity", primaryKeys = ["kind", "key1", "key2"])
data class OutboxEntryEntity(
  val kind: String,
  val key1: String,
  val key2: String = "",
  val deviceSeq: Long = 0L,
) {

  companion object {
    const val KIND_NODE = "NODE"
    const val KIND_EDGE = "EDGE"
    const val KIND_SETTING = "SETTING"
  }
}
