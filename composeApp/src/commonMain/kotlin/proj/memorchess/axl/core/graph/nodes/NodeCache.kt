package proj.memorchess.axl.core.graph.nodes

import co.touchlab.kermit.Logger
import proj.memorchess.axl.core.data.DataMove
import proj.memorchess.axl.core.data.DataNode
import proj.memorchess.axl.core.data.PositionIdentifier

abstract class NodeCache {

  /** Cache to prevent creating a node twice. */
  protected val movesCache = mutableMapOf<PositionIdentifier, PreviousAndNextMoves>()

  /**
   * Gets or creates a PreviousAndNextMoves entry for the given position key.
   *
   * @param positionIdentifier The position key to get or create an entry for.
   * @param depth Depth at which the returned value will be at maximum. If necessary, the previous
   *   value will be updated.
   * @return The PreviousAndNextMoves for the given position key.
   */
  fun getOrCreate(positionIdentifier: PositionIdentifier, depth: Int): PreviousAndNextMoves {
    val prevValue = movesCache[positionIdentifier]
    if (prevValue == null) {
      val result = PreviousAndNextMoves(depth)
      movesCache[positionIdentifier] = result
      return result
    }
    if (prevValue.depth > depth) {
      prevValue.depth = depth
    }
    return prevValue
  }

  /**
   * Gets the PreviousAndNextMoves for the given position key.
   *
   * @param positionIdentifier The position key to get the moves for.
   * @return The PreviousAndNextMoves for the given position key, or null if not present.
   */
  fun get(positionIdentifier: PositionIdentifier): PreviousAndNextMoves? {
    return movesCache[positionIdentifier]
  }

  /**
   * Clears all next moves for the given position key.
   *
   * @param positionIdentifier The position key to clear next moves for.
   */
  fun clearNextMoves(positionIdentifier: PositionIdentifier) {
    movesCache[positionIdentifier]?.nextMoves?.clear()
    LOGGER.i { "Cleared next moves for position: $positionIdentifier" }
  }

  abstract suspend fun resetFromSource()

  abstract fun getNodeFromDay(day: Int): DataNode?

  abstract fun getNodeToTrainAfterPosition(
    day: Int,
    positionIdentifier: PositionIdentifier,
  ): DataNode?

  abstract fun getNumberOfNodesToTrain(day: Int): Int

  abstract fun cacheNode(node: DataNode)

  abstract suspend fun clearPreviousMove(positionIdentifier: PositionIdentifier, move: DataMove)
}

private val LOGGER = Logger.withTag("NodeCache")
