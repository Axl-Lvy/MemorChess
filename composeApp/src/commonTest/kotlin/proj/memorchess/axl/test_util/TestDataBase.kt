package proj.memorchess.axl.test_util

import proj.memorchess.axl.core.data.ICommonDataBase
import proj.memorchess.axl.core.data.IStoredMove
import proj.memorchess.axl.core.data.IStoredNode
import proj.memorchess.axl.core.data.StoredMove
import proj.memorchess.axl.core.data.StoredNode
import proj.memorchess.axl.core.engine.Game
import proj.memorchess.axl.game.getLondon
import proj.memorchess.axl.game.getScandinavian
import proj.memorchess.axl.game.getVienna

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
      var previousMove: IStoredMove? = null
      for (move in moves) {
        val currentPosition = game.position.toImmutablePosition()
        game.playMove(move)
        val storedMove = StoredMove(currentPosition, game.position.toImmutablePosition(), move)
        val node =
          StoredNode(
            currentPosition,
            listOf(storedMove),
            previousMove?.let { listOf(it) } ?: emptyList(),
          )
        previousMove = storedMove
        testDataBase.storedNodes[node.positionKey.fenRepresentation] = node
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
            val newMoves = storedNode.nextMoves.toMutableSet()
            newMoves.addAll(entry.value.nextMoves)
            val previousMoves = storedNode.previousMoves.toMutableSet()
            newMoves.addAll(entry.value.previousMoves)
            merged.storedNodes[entry.key] =
              StoredNode(
                storedNode.positionKey,
                newMoves.sortedBy { it.move },
                previousMoves.sortedBy { it.move },
              )
          }
        }
      }
      return merged
    }
  }
}
