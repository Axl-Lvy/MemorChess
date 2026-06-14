package proj.memorchess.axl.test_util

import proj.memorchess.axl.core.data.DataMove
import proj.memorchess.axl.core.data.DataNode
import proj.memorchess.axl.core.data.InMemoryDatabaseQueryManager
import proj.memorchess.axl.core.data.PositionKey
import proj.memorchess.axl.core.engine.GameEngine
import proj.memorchess.axl.core.graph.PreviousAndNextMoves
import proj.memorchess.axl.core.scheduling.CardStateFactory

/**
 * In memory database for tests, prefilled with common openings.
 *
 * The storage itself is the production [InMemoryDatabaseQueryManager]; this subclass only adds the
 * test conveniences: a direct [dataNodes] view for assertions and the opening fixtures below. Using
 * the real implementation keeps the tests honest and exercises it from the whole suite.
 *
 * @constructor Creates an empty test database.
 */
class TestDatabaseQueryManager private constructor() : InMemoryDatabaseQueryManager() {

  /** Direct view of the stored nodes (including soft-deleted ones) for assertions and seeding. */
  val dataNodes: MutableMap<PositionKey, DataNode>
    get() = nodes

  /**
   * Utility methods to create test databases prefilled with common chess openings, as well as to
   * merge multiple test databases.
   */
  companion object {

    /** A database with the Vienna opening. */
    fun vienna(): TestDatabaseQueryManager {
      return createDataBaseFromMoves(getVienna())
    }

    /** A database with the London opening. */
    fun london(): TestDatabaseQueryManager {
      return createDataBaseFromMoves(getLondon())
    }

    /** A database with the Scandinavian opening. */
    fun scandinavian(): TestDatabaseQueryManager {
      return createDataBaseFromMoves(getScandinavian())
    }

    /** An empty database. */
    fun empty(): TestDatabaseQueryManager {
      return TestDatabaseQueryManager()
    }

    /**
     * A database with the given moves.
     *
     * @param moves The moves to create the database from.
     * @return A [TestDatabaseQueryManager] with the given moves.
     */
    private fun createDataBaseFromMoves(moves: List<String>): TestDatabaseQueryManager {
      val testDataBase = TestDatabaseQueryManager()
      val nodes = convertStringMovesToNodes(moves)
      for (node in nodes) {
        testDataBase.dataNodes[node.positionKey] = node
      }
      return testDataBase
    }

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
          )
        previousMove = dataMove
        nodes.add(node)
      }
      return nodes
    }

    /**
     * Merges multiple [TestDatabaseQueryManager] instances into one.
     *
     * @param testDataBases The databases to merge.
     * @return A new [TestDatabaseQueryManager] containing all positions and their moves.
     */
    fun merge(vararg testDataBases: TestDatabaseQueryManager): TestDatabaseQueryManager {
      val merged = TestDatabaseQueryManager()
      for (dataBase in testDataBases) {
        for ((positionKey, node) in dataBase.dataNodes) {
          val storedNode = merged.dataNodes[positionKey]
          if (storedNode == null) {
            merged.dataNodes[positionKey] = node
          } else {
            val newMoves = storedNode.previousAndNextMoves.nextMoves.values.toMutableSet()
            newMoves.addAll(node.previousAndNextMoves.nextMoves.values)
            val previousMoves = storedNode.previousAndNextMoves.previousMoves.values.toMutableSet()
            newMoves.addAll(node.previousAndNextMoves.previousMoves.values)
            merged.dataNodes[positionKey] =
              DataNode(
                storedNode.positionKey,
                PreviousAndNextMoves(previousMoves, newMoves),
                CardStateFactory.new(),
              )
          }
        }
      }
      return merged
    }
  }
}
