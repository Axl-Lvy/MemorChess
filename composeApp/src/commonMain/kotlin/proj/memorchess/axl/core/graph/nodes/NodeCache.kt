package proj.memorchess.axl.core.graph.nodes

import co.touchlab.kermit.Logger
import proj.memorchess.axl.core.data.DataMove
import proj.memorchess.axl.core.data.DataNode
import proj.memorchess.axl.core.data.PositionKey

/**
 * NodeCache to abstract operations on the moves cache. This class manages the cache of position
 * keys and their associated moves.
 */
abstract class NodeCache {

  /** Cache to prevent creating a node twice. */
  protected val movesCache = mutableMapOf<PositionKey, PreviousAndNextMoves>()

  /**
   * Gets or creates a PreviousAndNextMoves entry for the given position key.
   *
   * @param positionKey The position key to get or create an entry for.
   * @param depth Depth at which the returned value will be at maximum. If necessary, the previous
   *   value will be updated.
   * @return The PreviousAndNextMoves for the given position key.
   */
  fun getOrCreate(positionKey: PositionKey, depth: Int): PreviousAndNextMoves {
    val prevValue = movesCache[positionKey]
    if (prevValue == null) {
      val result = PreviousAndNextMoves(depth)
      movesCache[positionKey] = result
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
   * @param positionKey The position key to get the moves for.
   * @return The PreviousAndNextMoves for the given position key, or null if not present.
   */
  fun get(positionKey: PositionKey): PreviousAndNextMoves? {
    return movesCache[positionKey]
  }

  /**
   * Clears all next moves for the given position key.
   *
   * @param positionKey The position key to clear next moves for.
   */
  fun clearNextMoves(positionKey: PositionKey) {
    movesCache[positionKey]?.nextMoves?.clear()
    LOGGER.i { "Cleared next moves for position: $positionKey" }
  }

  /** Clears the entire cache and reloads it from the source. */
  abstract suspend fun resetFromSource()

  /** Gets a node scheduled for training after a number of days. */
  abstract fun getNodeFromDay(day: Int): DataNode?

  /** Gets a node scheduled for training after a number of days, following a specific position. */
  abstract fun getNodeToTrainAfterPosition(day: Int, positionKey: PositionKey): DataNode?

  /** Gets the number of nodes scheduled for training after a number of days. */
  abstract fun getNumberOfNodesToTrain(day: Int): Int

  /** Caches the given node. */
  abstract fun cacheNode(node: DataNode)

  /**
   * Clears a specific previous move for the given position key.
   *
   * @param positionKey The position key to clear the previous move for.
   * @param move The move to clear.
   */
  suspend fun clearPreviousMove(positionKey: PositionKey, move: DataMove) {
    movesCache[positionKey]?.previousMoves?.remove(move.move)
    LOGGER.i { "Cleared previous move $move for position: $positionKey" }
    deleteMove(move)
  }

  abstract suspend fun deleteMove(move: DataMove)
}

private val LOGGER = Logger.withTag("NodeCache")
