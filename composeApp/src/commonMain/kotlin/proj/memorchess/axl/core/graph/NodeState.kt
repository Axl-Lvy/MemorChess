package proj.memorchess.axl.core.graph

/**
 * Represents the state of a node according to the database.
 *
 * @property saved Whether the node has been saved to the database.
 * @property good Whether the node is good.
 * @property previousMoveKnown Whether the previous move is known.
 */
enum class NodeState(
  private val saved: Boolean,
  private val good: Boolean,
  private val previousMoveKnown: Boolean,
) {
  /** This node the first one. */
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
  /**
   * Node in a bad state. For example if a bad move and a good move lead to it.
   *
   * It should be removed.
   */
  BAD_STATE(true, false, true),
}
