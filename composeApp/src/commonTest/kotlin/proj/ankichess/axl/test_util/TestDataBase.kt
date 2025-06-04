package proj.ankichess.axl.test_util

import proj.ankichess.axl.core.data.ICommonDataBase
import proj.ankichess.axl.core.data.IStoredNode
import proj.ankichess.axl.core.data.StoredNode
import proj.ankichess.axl.core.engine.Game
import proj.ankichess.axl.game.getLondon
import proj.ankichess.axl.game.getScandinavian
import proj.ankichess.axl.game.getVienna

/**
 * A test in-memory database.
 *
 * @constructor Creates an empty test database.
 */
class TestDataBase private constructor() : ICommonDataBase {
  val storedNodes = mutableMapOf<String, IStoredNode>()

  override suspend fun getAllPositions(): List<IStoredNode> {
    return storedNodes.values.toList()
  }

  override suspend fun deletePosition(fen: String) {
    storedNodes.remove(fen)
  }

  override suspend fun insertPosition(position: IStoredNode) {
    storedNodes[position.positionKey.fenRepresentation] = position
  }

  override suspend fun deleteAllPositions() {
    storedNodes.clear()
  }

  /**
   * Utility methods to create test databases prefilled with common chess openings, as well as to
   * merge multiple test databases.
   */
  companion object {

    /** A database with the Vienna opening. */
    fun vienna(): TestDataBase {
      return createDataBaseFromMoves(getVienna())
    }

    /** A database with the London opening. */
    fun london(): TestDataBase {
      return createDataBaseFromMoves(getLondon())
    }

    /** A database with the Scandinavian opening. */
    fun scandinavian(): TestDataBase {
      return createDataBaseFromMoves(getScandinavian())
    }

    /** An empty database. */
    fun empty(): TestDataBase {
      return TestDataBase()
    }

    /**
     * A database with the given moves.
     *
     * @param moves The moves to create the database from.
     * @return A [TestDataBase] with the given moves.
     */
    private fun createDataBaseFromMoves(moves: List<String>): TestDataBase {
      val testDataBase = TestDataBase()
      val game = Game()
      var previousMove: String? = null
      for (move in moves) {
        val node =
          StoredNode(
            game.position.toImmutablePosition(),
            listOf(move),
            previousMove?.let { listOf(it) } ?: emptyList(),
          )
        previousMove = move
        testDataBase.storedNodes[node.positionKey.fenRepresentation] = node
        game.playMove(move)
      }
      return testDataBase
    }

    /**
     * Merges multiple [TestDataBase] instances into one.
     *
     * @param testDataBases The databases to merge.
     * @return A new [TestDataBase] containing all positions and their moves.
     */
    fun merge(vararg testDataBases: TestDataBase): TestDataBase {
      val merged = TestDataBase()
      for (dataBase in testDataBases) {
        for (entry in dataBase.storedNodes) {
          val storedNode = merged.storedNodes[entry.key]
          if (storedNode == null) {
            merged.storedNodes[entry.key] = entry.value
          } else {
            val newMoves = storedNode.getAvailableMoveList().toMutableSet()
            newMoves.addAll(entry.value.getAvailableMoveList())
            val previousMoves = storedNode.getPreviousMoveList().toMutableSet()
            newMoves.addAll(entry.value.getPreviousMoveList())
            merged.storedNodes[entry.key] =
              StoredNode(storedNode.positionKey, newMoves.sorted(), previousMoves.sorted())
          }
        }
      }
      return merged
    }
  }
}
