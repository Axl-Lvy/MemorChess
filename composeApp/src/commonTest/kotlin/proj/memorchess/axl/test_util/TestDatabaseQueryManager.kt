package proj.memorchess.axl.test_util

import kotlinx.datetime.LocalDateTime
import proj.memorchess.axl.core.data.DatabaseQueryManager
import proj.memorchess.axl.core.data.PositionIdentifier
import proj.memorchess.axl.core.data.StoredMove
import proj.memorchess.axl.core.data.StoredNode
import proj.memorchess.axl.core.date.PreviousAndNextDate
import proj.memorchess.axl.core.engine.Game
import proj.memorchess.axl.core.graph.nodes.PreviousAndNextMoves
import proj.memorchess.axl.game.getLondon
import proj.memorchess.axl.game.getScandinavian
import proj.memorchess.axl.game.getVienna

/**
 * A test in-memory database.
 *
 * @constructor Creates an empty test database.
 */
class TestDatabaseQueryManager private constructor() : DatabaseQueryManager {
  val storedNodes = mutableMapOf<String, StoredNode>()

  override suspend fun getAllNodes(): List<StoredNode> {
    return storedNodes.values.toList()
  }

  override suspend fun getPosition(positionIdentifier: PositionIdentifier): StoredNode? {
    return storedNodes[positionIdentifier.fenRepresentation]
  }

  override suspend fun deletePosition(fen: String) {
    storedNodes.remove(fen)
  }

  override suspend fun deleteMove(origin: String, move: String) {
    if (storedNodes[origin] == null) {
      return
    }
    val storedNode = storedNodes[origin]!!
    val newNextMoves = storedNode.previousAndNextMoves.nextMoves.values.filter { it.move != move }
    storedNodes[origin] =
      StoredNode(
        PositionIdentifier(origin),
        PreviousAndNextMoves(storedNode.previousAndNextMoves.previousMoves.values, newNextMoves),
        storedNode.previousAndNextTrainingDate,
      )
  }

  override suspend fun getAllMoves(withDeletedOnes: Boolean): List<StoredMove> {
    return storedNodes.values
      .flatMap {
        it.previousAndNextMoves.nextMoves.values + it.previousAndNextMoves.previousMoves.values
      }
      .distinct()
  }

  override suspend fun deleteAll(hardFrom: LocalDateTime?) {
    storedNodes.clear()
  }

  override suspend fun insertNodes(vararg positions: StoredNode) {
    positions.forEach { storedNodes[it.positionIdentifier.fenRepresentation] = it }
  }

  override suspend fun getLastUpdate(): LocalDateTime? {
    throw UnsupportedOperationException("Not yet implemented")
  }

  override fun isActive(): Boolean {
    return true
  }

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
        testDataBase.storedNodes[node.positionIdentifier.fenRepresentation] = node
      }
      return testDataBase
    }

    fun convertStringMovesToNodes(moves: List<String>): List<StoredNode> {
      val nodes = mutableListOf<StoredNode>()
      val game = Game()
      var previousMove: StoredMove? = null
      for ((depth, move) in moves.withIndex()) {
        val currentPosition = game.position.createIdentifier()
        game.playMove(move)
        val storedMove = StoredMove(currentPosition, game.position.createIdentifier(), move, true)
        val node =
          StoredNode(
            currentPosition,
            PreviousAndNextMoves(
              previousMove?.let { listOf(it) } ?: listOf(),
              listOf(storedMove),
              depth,
            ),
            PreviousAndNextDate.dummyToday(),
          )
        previousMove = storedMove
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
        for (entry in dataBase.storedNodes) {
          val storedNode = merged.storedNodes[entry.key]
          if (storedNode == null) {
            merged.storedNodes[entry.key] = entry.value
          } else {
            val newMoves = storedNode.previousAndNextMoves.nextMoves.values.toMutableSet()
            newMoves.addAll(entry.value.previousAndNextMoves.nextMoves.values)
            val previousMoves = storedNode.previousAndNextMoves.previousMoves.values.toMutableSet()
            newMoves.addAll(entry.value.previousAndNextMoves.previousMoves.values)
            merged.storedNodes[entry.key] =
              StoredNode(
                storedNode.positionIdentifier,
                PreviousAndNextMoves(previousMoves, newMoves),
                PreviousAndNextDate.dummyToday(),
              )
          }
        }
      }
      return merged
    }
  }
}
