package proj.memorchess.axl.core.graph.nodes

import co.touchlab.kermit.Logger
import proj.memorchess.axl.core.data.DataMove
import proj.memorchess.axl.core.data.DataNode
import proj.memorchess.axl.core.data.PositionKey
import proj.memorchess.axl.core.graph.OpeningTree
import proj.memorchess.axl.core.graph.TrainingSchedule
import proj.memorchess.axl.core.graph.TreeRepository

/**
 * NodeManager is responsible for managing nodes in the chess position graph.
 *
 * @property openingTree The opening tree holding the graph structure.
 * @property treeRepository The repository for persistence operations.
 * @property trainingSchedule Optional training schedule for spaced-repetition.
 */
class NodeManager(
  val openingTree: OpeningTree,
  val treeRepository: TreeRepository,
  private val trainingSchedule: TrainingSchedule? = null,
) {

  /** Resets the cache from the source. */
  suspend fun resetCacheFromSource() {
    treeRepository.loadInto(openingTree, trainingSchedule)
  }

  /** Registers a move in the opening tree and returns the [DataMove]. */
  fun registerMove(
    origin: PositionKey,
    destination: PositionKey,
    move: String,
    depth: Int,
  ): DataMove {
    val originMoves = openingTree.getOrCreate(origin, depth)
    val dataMove = originMoves.nextMoves.getOrPut(move) { DataMove(origin, destination, move) }
    val destMoves = openingTree.getOrCreate(destination, depth + 1)
    val prev = destMoves.addPreviousMove(dataMove)
    if (prev != null && prev != dataMove) {
      LOGGER.w { "Overwriting previous move: $prev with $dataMove" }
    }
    return dataMove
  }

  fun clearNextMoves(positionKey: PositionKey) {
    openingTree.clearNextMoves(positionKey)
    LOGGER.i { "Cleared next moves for position: $positionKey" }
  }

  suspend fun clearPreviousMove(positionKey: PositionKey, move: DataMove) {
    openingTree.clearPreviousMove(positionKey, move.move)
    LOGGER.i { "Cleared previous move $move for position: $positionKey" }
    treeRepository.deleteMove(move.origin, move.move)
  }

  /** Gets the next node to learn for the given day. */
  fun getNextNodeToLearn(day: Int, previousPlayedMove: DataMove?): DataNode? {
    val schedule = trainingSchedule ?: return null
    if (previousPlayedMove == null) {
      return schedule.getNodeFromDay(day)
    }
    return schedule.getNodeToTrainAfterPosition(day, previousPlayedMove.destination)
      ?: schedule.getNodeFromDay(day)
  }

  /** Gets the number of nodes to train for the given day. */
  fun getNumberOfNodesToTrain(day: Int): Int {
    return trainingSchedule?.getNumberOfNodesToTrain(day) ?: 0
  }

  /** Caches a node in the training schedule. */
  fun cacheNode(node: DataNode) {
    trainingSchedule?.addNode(node)
  }

  /** Checks if a position is known in the tree. */
  fun isKnown(position: PositionKey): Boolean {
    return openingTree.get(position) != null
  }
}

private val LOGGER = Logger.withTag("NodeManager")
