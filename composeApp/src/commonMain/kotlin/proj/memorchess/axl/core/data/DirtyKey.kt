package proj.memorchess.axl.core.data

/**
 * A key awaiting the next sync push; repeat edits collapse into one outbox entry. The pusher reads
 * the row fresh at push time, so the queue never replays a stale value.
 */
sealed interface DirtyKey {

  /** A dirty position, keyed the same way the node row itself is. */
  data class NodeKey(val positionKey: PositionKey) : DirtyKey

  /** A dirty move, keyed by its endpoints. */
  data class EdgeKey(val origin: PositionKey, val destination: PositionKey) : DirtyKey

  /** A dirty setting, keyed by its [proj.memorchess.axl.core.config.ConfigItem.name]. */
  data class SettingKey(val key: String) : DirtyKey

  /** A dirty repertoire registry row, keyed by its id. */
  data class RepertoireKey(val repertoireId: String) : DirtyKey

  /** A dirty edge to repertoire tag, keyed by the edge's endpoints and the repertoire. */
  data class TagKey(
    val origin: PositionKey,
    val destination: PositionKey,
    val repertoireId: String,
  ) : DirtyKey
}

/**
 * A [DirtyKey] paired with the `deviceSeq` of the write it names, so
 * [DatabaseQueryManager.clearDirty] keeps an entry re-marked dirty between the push read and the
 * clear.
 */
data class OutboxEntry(val key: DirtyKey, val deviceSeq: Long)
