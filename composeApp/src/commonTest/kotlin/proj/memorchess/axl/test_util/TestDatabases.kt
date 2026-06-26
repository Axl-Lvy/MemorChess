package proj.memorchess.axl.test_util

import proj.memorchess.axl.core.data.DataMove
import proj.memorchess.axl.core.data.DataNode
import proj.memorchess.axl.core.data.InMemoryDatabaseQueryManager
import proj.memorchess.axl.core.data.PositionKey
import proj.memorchess.axl.core.engine.GameEngine
import proj.memorchess.axl.core.graph.PreviousAndNextMoves
import proj.memorchess.axl.core.scheduling.CardStateFactory

/**
 * Factories for [InMemoryDatabaseQueryManager] instances prefilled with common openings.
 *
 * These are plain fixtures over the production [InMemoryDatabaseQueryManager]: they seed and read
 * the graph exclusively through the public [proj.memorchess.axl.core.data.DatabaseQueryManager]
 * API, so they exercise the same code path the app uses and never reach into the storage map.
 */
object TestDatabases {

  /** An empty database. */
  fun empty(): InMemoryDatabaseQueryManager = InMemoryDatabaseQueryManager()

  /** A database with the Vienna opening. */
  suspend fun vienna(): InMemoryDatabaseQueryManager = fromMoves(getVienna())

  /** A database with the London opening. */
  suspend fun london(): InMemoryDatabaseQueryManager = fromMoves(getLondon())

  /** A database with the Scandinavian opening. */
  suspend fun scandinavian(): InMemoryDatabaseQueryManager = fromMoves(getScandinavian())

  /**
   * Converts a list of SAN moves into the chain of [DataNode]s that represents that single line.
   *
   * @param moves The moves to convert.
   * @return One [DataNode] per position along the line, in play order.
   */
  fun convertStringMovesToNodes(moves: List<String>): List<DataNode> {
    val nodes = mutableListOf<DataNode>()
    val engine = GameEngine()
    var previousMove: DataMove? = null
    for ((depth, move) in moves.withIndex()) {
      val currentPosition = engine.toPositionKey()
      engine.playSanMove(move)
      val dataMove = DataMove(currentPosition, engine.toPositionKey(), move, true)
      val node =
        DataNode(
          currentPosition,
          PreviousAndNextMoves(previousMove?.let { listOf(it) } ?: listOf(), listOf(dataMove)),
          CardStateFactory.new(),
          depth,
          hasGoodOutgoing = true,
        )
      previousMove = dataMove
      nodes.add(node)
    }
    return nodes
  }

  /**
   * Merges multiple databases into one, unioning the moves of any position that appears in more
   * than one of them.
   *
   * @param databases The databases to merge.
   * @return A new database containing all positions and their moves.
   */
  suspend fun merge(vararg databases: InMemoryDatabaseQueryManager): InMemoryDatabaseQueryManager {
    val mergedNodes = mutableMapOf<PositionKey, DataNode>()
    for (database in databases) {
      for (node in drainAllNodes(database)) {
        val storedNode = mergedNodes[node.positionKey]
        if (storedNode == null) {
          mergedNodes[node.positionKey] = node
        } else {
          val newMoves = storedNode.previousAndNextMoves.nextMoves.values.toMutableSet()
          newMoves.addAll(node.previousAndNextMoves.nextMoves.values)
          val previousMoves = storedNode.previousAndNextMoves.previousMoves.values.toMutableSet()
          newMoves.addAll(node.previousAndNextMoves.previousMoves.values)
          mergedNodes[node.positionKey] =
            DataNode(
              storedNode.positionKey,
              PreviousAndNextMoves(previousMoves, newMoves),
              CardStateFactory.new(),
              hasGoodOutgoing = newMoves.any { it.isGood == true && !it.isDeleted },
            )
        }
      }
    }
    val merged = InMemoryDatabaseQueryManager()
    merged.insertNodes(*mergedNodes.values.toTypedArray())
    return merged
  }

  /** Builds a database holding the single line described by [moves]. */
  private suspend fun fromMoves(moves: List<String>): InMemoryDatabaseQueryManager {
    val database = InMemoryDatabaseQueryManager()
    database.insertNodes(*convertStringMovesToNodes(moves).toTypedArray())
    return database
  }
}
