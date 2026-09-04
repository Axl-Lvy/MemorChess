package proj.memorchess.axl.core.graph

/**
 * How a delete operation removes data from the store.
 *
 * [SOFT] is the default: a synchronisation layer may exist, so a delete has to leave a tombstone
 * rather than erase history another device has not seen yet. [HARD] survives for local only cleanup
 * ([proj.memorchess.axl.core.data.DatabaseQueryManager.eraseAll] and tests) where there is nothing
 * to reconcile.
 */
enum class DeleteMode {
  /** Physically removes the row from the underlying store. */
  HARD,

  /** Marks the row as deleted and bumps its `updatedAt`, `originDevice` and `deviceSeq`. */
  SOFT,
}
