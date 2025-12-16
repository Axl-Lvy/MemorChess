package proj.memorchess.axl.core.graph.nodes

import co.touchlab.kermit.Logger
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import proj.memorchess.axl.core.data.DataMove
import proj.memorchess.axl.core.data.DataNode
import proj.memorchess.axl.core.data.DatabaseQueryManager
import proj.memorchess.axl.core.data.PositionIdentifier
import proj.memorchess.axl.core.date.DateUtil

/**
 * NodeCache singleton to abstract operations on the moves cache. This class manages the cache of
 * position keys and their associated moves.
 */
class DbBasedNodeCache : NodeCache(), KoinComponent {

  private val database by inject<DatabaseQueryManager>()

  private val nodesByDay = mutableMapOf<Int, MutableMap<PositionIdentifier, DataNode>>()

  /**
   * Clears a specific previous move for the given position key.
   *
   * @param positionIdentifier The position key to clear the previous move for.
   * @param move The move to clear.
   */
  override suspend fun clearPreviousMove(positionIdentifier: PositionIdentifier, move: DataMove) {
    movesCache[positionIdentifier]?.previousMoves?.remove(move.move)
    LOGGER.i { "Cleared previous move $move for position: $positionIdentifier" }
    database.deleteMove(positionIdentifier, move.move)
  }

  override fun cacheNode(node: DataNode) {
    movesCache[node.positionIdentifier] = node.previousAndNextMoves
    val daysUntil = DateUtil.daysUntil(node.previousAndNextTrainingDate.nextDate)
    nodesByDay.getOrPut(daysUntil) { mutableMapOf() }[node.positionIdentifier] = node
  }

  override fun getNodeFromDay(day: Int): DataNode? {
    val candidates = nodesByDay[day] ?: return null
    val position =
      candidates.entries.minByOrNull { it.value.previousAndNextMoves.depth }?.key ?: return null
    return candidates.remove(position)
  }

  override fun getNodeToTrainAfterPosition(
    day: Int,
    positionIdentifier: PositionIdentifier,
  ): DataNode? {
    val todayNodes = nodesByDay[day] ?: return null
    for (candidatePosition in
      movesCache[positionIdentifier]?.nextMoves?.values?.map { it.destination } ?: emptyList()) {
      val candidateNode = todayNodes.remove(candidatePosition)
      if (candidateNode != null) {
        return candidateNode
      }
    }
    return null
  }

  override fun getNumberOfNodesToTrain(day: Int): Int {
    return nodesByDay[day]?.size ?: 0
  }

  /** Retrieves the graph from the database and populates the cache. */
  override suspend fun resetFromSource() {
    clear()
    val allNodes: List<DataNode> = database.getAllNodes()
    allNodes.forEach { node ->
      val previousAndNextMoves = node.previousAndNextMoves.filterNotDeleted()
      movesCache.getOrPut(node.positionIdentifier) { previousAndNextMoves }
      if (previousAndNextMoves.nextMoves.any { it.value.isGood == true }) {
        val daysUntil = DateUtil.daysUntil(node.previousAndNextTrainingDate.nextDate)
        nodesByDay.getOrPut(daysUntil) { mutableMapOf() }[node.positionIdentifier] = node
      }
      LOGGER.i { "Retrieved node: ${node.positionIdentifier}" }
    }
  }

  /** Clears the cache and resets the database retrieved flag. */
  private fun clear() {
    nodesByDay.clear()
    movesCache.clear()
  }
}

private val LOGGER = Logger.withTag("DbBasedNodeCache")
