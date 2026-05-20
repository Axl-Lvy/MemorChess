package proj.memorchess.axl.core.graph

/**
 * How a delete operation removes data from the store.
 *
 * The app is local only, so [HARD] is the default. [SOFT] exists for a future synchronisation layer
 * that needs to retain tombstones; no caller in the current code base uses it yet.
 */
enum class DeleteMode {
  /** Physically removes the row from the underlying store. */
  HARD,

  /** Marks the row as deleted and bumps its `updatedAt` timestamp without removing it. */
  SOFT,
}
