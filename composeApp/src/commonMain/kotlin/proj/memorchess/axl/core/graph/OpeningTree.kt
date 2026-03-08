package proj.memorchess.axl.core.graph

import proj.memorchess.axl.core.data.DataMove
import proj.memorchess.axl.core.data.PositionKey

/** Pure graph of chess positions and moves, keyed by [PositionKey]. */
class OpeningTree {

  private val positions = mutableMapOf<PositionKey, PreviousAndNextMoves>()

  /** Get-or-create, updating depth to the minimum seen. */
  fun getOrCreate(positionKey: PositionKey, depth: Int): PreviousAndNextMoves {
    val prev = positions[positionKey]
    if (prev == null) {
      val result = PreviousAndNextMoves(depth)
      positions[positionKey] = result
      return result
    }
    if (prev.depth > depth) prev.depth = depth
    return prev
  }

  /** Gets moves for the given position, or null if not present. */
  fun get(positionKey: PositionKey): PreviousAndNextMoves? = positions[positionKey]

  /** Sets moves for the given position. */
  fun put(positionKey: PositionKey, moves: PreviousAndNextMoves) {
    positions[positionKey] = moves
  }

  /** Gets or inserts a default value for the given position. */
  fun getOrPut(
    positionKey: PositionKey,
    default: () -> PreviousAndNextMoves,
  ): PreviousAndNextMoves {
    return positions.getOrPut(positionKey, default)
  }

  /** Checks if a position is known in the tree. */
  fun isKnown(positionKey: PositionKey): Boolean = positionKey in positions

  /** Clears all next moves for the given position. */
  fun clearNextMoves(positionKey: PositionKey) {
    positions[positionKey]?.nextMoves?.clear()
  }

  /** Clears a specific previous move for the given position. */
  fun clearPreviousMove(positionKey: PositionKey, moveString: String) {
    positions[positionKey]?.previousMoves?.remove(moveString)
  }

  /** Clears the entire tree. */
  fun clear() {
    positions.clear()
  }

  /**
   * Compute state for a position given which position we arrived from.
   *
   * @param positionKey The position to compute state for.
   * @param arrivedFrom The position we arrived from (null at root).
   * @return The computed [NodeState].
   */
  fun computeState(positionKey: PositionKey, arrivedFrom: PositionKey?): NodeState {
    val moves = positions[positionKey] ?: return NodeState.UNKNOWN
    val previousMoves = moves.previousMoves
    if (previousMoves.isEmpty()) return NodeState.FIRST

    var isGood: Boolean? = null
    var isPreviousMoveGood: Boolean? = null
    previousMoves.forEach {
      if (it.value.isGood == true) {
        if (isGood == false) return NodeState.BAD_STATE
        isGood = true
      } else if (it.value.isGood == false) {
        if (isGood == true) return NodeState.BAD_STATE
        isGood = false
      }
      if (arrivedFrom != null && arrivedFrom == it.value.origin) {
        isPreviousMoveGood = it.value.isGood
      }
    }
    return determineState(isGood, isPreviousMoveGood)
  }

  /**
   * Count positions in the subtree that would be deleted.
   *
   * @param positionKey The root position for deletion counting.
   * @param viaMove The move that led to this position (null for root of deletion).
   * @return The number of positions that would be deleted.
   */
  fun countDescendants(positionKey: PositionKey, viaMove: DataMove? = null): Int {
    val moves = positions[positionKey] ?: return 0
    if (viaMove != null && moves.previousMoves.values.any { it.move != viaMove.move }) {
      return 0
    }
    var count = 1
    moves.nextMoves.values.forEach { nextMove ->
      count += countDescendants(nextMove.destination, nextMove)
    }
    return count
  }

  private fun determineState(isGood: Boolean?, isPreviousMoveGood: Boolean?): NodeState {
    return when (isGood) {
      null ->
        when (isPreviousMoveGood) {
          null -> NodeState.UNKNOWN
          else -> NodeState.BAD_STATE
        }
      true ->
        when (isPreviousMoveGood) {
          null -> NodeState.SAVED_GOOD_BUT_UNKNOWN_MOVE
          true -> NodeState.SAVED_GOOD
          false -> NodeState.BAD_STATE
        }
      false ->
        when (isPreviousMoveGood) {
          null -> NodeState.SAVED_BAD_BUT_UNKNOWN_MOVE
          true -> NodeState.BAD_STATE
          false -> NodeState.SAVED_BAD
        }
    }
  }
}
