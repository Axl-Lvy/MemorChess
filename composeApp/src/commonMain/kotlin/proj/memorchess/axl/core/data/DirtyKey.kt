package proj.memorchess.axl.core.data

/**
 * One dirty key awaiting the next sync push, never a row: see the sync design doc section 5.6.
 * Repeat edits to the same key collapse into one outbox entry, and the queue can never go stale
 * because the pusher reads the row fresh at push time instead of replaying a stored value.
 */
sealed interface DirtyKey {

  /** A dirty position, keyed the same way the node row itself is. */
  data class NodeKey(val positionKey: PositionKey) : DirtyKey

  /**
   * A dirty move, keyed by its endpoints exactly like [DataMove.origin] and [DataMove.destination].
   */
  data class EdgeKey(val origin: PositionKey, val destination: PositionKey) : DirtyKey

  /** A dirty setting, keyed by its [proj.memorchess.axl.core.config.ConfigItem.name]. */
  data class SettingKey(val key: String) : DirtyKey
}

/**
 * One outbox row: a [DirtyKey] paired with the `deviceSeq` of the write it names.
 *
 * The pairing is what lets [DatabaseQueryManager.clearDirty] stay safe against the read-push-clear
 * race: a clear only removes an entry whose stored sequence has not moved past the one that was
 * actually pushed, so a [DatabaseQueryManager.markDirty] landing between the read and the clear
 * survives instead of being silently dropped. [DatabaseQueryManager.getOutbox] returns entries
 * ordered ascending by [deviceSeq], which callers may treat as the push order.
 */
data class OutboxEntry(val key: DirtyKey, val deviceSeq: Long)
