package proj.memorchess.axl.core.graph

import co.touchlab.kermit.Logger
import proj.memorchess.axl.core.data.DataNode
import proj.memorchess.axl.core.data.DatabaseQueryManager
import proj.memorchess.axl.core.data.PositionKey

/** [TreeRepository] backed by the local database. */
class DbTreeRepository(private val database: DatabaseQueryManager) : TreeRepository {

  override suspend fun loadInto(tree: OpeningTree, trainingSchedule: TrainingSchedule?) {
    tree.clear()
    trainingSchedule?.clear()
    val allNodes = database.getAllNodes()
    allNodes.forEach { node ->
      val moves = node.previousAndNextMoves.filterNotDeleted()
      tree.getOrPut(node.positionKey) { moves.toMutable() }
      tree.updateDepth(node.positionKey, node.depth)
      if (moves.nextMoves.any { it.value.isGood == true }) {
        trainingSchedule?.addEntry(
          TrainingEntry(node.positionKey, node.previousAndNextTrainingDate)
        )
      }
      LOGGER.i { "Retrieved node: ${node.positionKey}" }
    }
  }

  override suspend fun saveNode(node: DataNode) {
    LOGGER.i { "saving $node" }
    database.insertNodes(node)
  }

  override suspend fun deletePosition(positionKey: PositionKey) {
    database.deletePosition(positionKey)
  }

  override suspend fun deleteMove(origin: PositionKey, move: String) {
    database.deleteMove(origin, move)
  }
}

private val LOGGER = Logger.withTag("DbTreeRepository")
