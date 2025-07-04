package proj.memorchess.axl.core.engine.moves

/** Simple representation of a move */
interface IMove {

  /** Destination of the move */
  fun destination(): Pair<Int, Int>

  /** Origin of the move */
  fun origin(): Pair<Int, Int>

  /**
   * A -> B means: **at the position A, put the piece currently at B**, or nothing if `B == null`.
   */
  fun generateChanges(): Map<Pair<Int, Int>, Pair<Int, Int>?>
}
