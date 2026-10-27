package proj.memorchess.axl.core.graph

/** State of a node according to the database. */
enum class NodeState(
  private val saved: Boolean,
  private val good: Boolean,
  private val previousMoveKnown: Boolean,
) {
  /** The starting node. */
  FIRST(true, true, true),
  /** Node not stored. */
  UNKNOWN(false, false, false),
  /** Node stored as good. Its previous move is also stored. */
  SAVED_GOOD(true, true, true),
  /** Node stored as bad. Its previous move is also stored. */
  SAVED_BAD(true, false, true),
  /** Node stored as good but from another move. */
  SAVED_GOOD_BUT_UNKNOWN_MOVE(true, true, false),
  /** Node stored as bad but from another move. */
  SAVED_BAD_BUT_UNKNOWN_MOVE(true, false, false),
  /** Inconsistent node, e.g. reached by both a good and a bad move; it should be removed. */
  BAD_STATE(true, false, true),
}
