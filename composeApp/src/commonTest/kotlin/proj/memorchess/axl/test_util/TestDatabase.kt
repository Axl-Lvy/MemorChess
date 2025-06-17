package proj.memorchess.axl.test_util

import proj.memorchess.axl.core.data.ICommonDatabase
import proj.memorchess.axl.core.data.IStoredNode
import proj.memorchess.axl.core.data.PositionKey
import proj.memorchess.axl.core.data.StoredMove
import proj.memorchess.axl.core.data.StoredNode
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
class TestDatabase private constructor() : ICommonDatabase {
  val storedNodes = mutableMapOf<String, StoredNode>()

  override suspend fun getAllPositions(): List<StoredNode> {
    return storedNodes.values.toList()
  }

  override suspend fun deletePosition(fen: String) {
    storedNodes.remove(fen)
  }

  override suspend fun deleteMoveFrom(origin: String) {
    if (storedNodes[origin] == null) {
      return
    }
    storedNodes[origin] =
      StoredNode(PositionKey(origin), storedNodes[origin]!!.previousMoves, mutableListOf())
  }

  override suspend fun deleteMoveTo(destination: String) {
    if (storedNodes[destination] == null) {
      return
    }
    storedNodes[destination] =
      StoredNode(PositionKey(destination), mutableListOf(), storedNodes[destination]!!.nextMoves)
  }

  override suspend fun deleteMove(origin: String, move: String) {
    if (storedNodes[origin] == null) {
      return
    }
    val newNextMoves = storedNodes[origin]!!.nextMoves.filter { it.move != move }
    storedNodes[origin] =
      StoredNode(
        PositionKey(origin),
        PreviousAndNextMoves(storedNodes[origin]!!.previousMoves, newNextMoves),
      )
  }

  override suspend fun insertMove(move: StoredMove) {
    storedNodes[move.origin.fenRepresentation]!!.nextMoves.add(move)
    storedNodes[move.destination.fenRepresentation]!!.previousMoves.add(move)
  }

  override suspend fun insertPosition(position: IStoredNode) {
    storedNodes[position.positionKey.fenRepresentation] = position as StoredNode
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
    fun vienna(): TestDatabase {
      return createDataBaseFromMoves(getVienna())
    }

    /** A database with the London opening. */
    fun london(): TestDatabase {
      return createDataBaseFromMoves(getLondon())
    }

    /** A database with the Scandinavian opening. */
    fun scandinavian(): TestDatabase {
      return createDataBaseFromMoves(getScandinavian())
    }

    /** An empty database. */
    fun empty(): TestDatabase {
      return TestDatabase()
    }

    /**
     * A database with the given moves.
     *
     * @param moves The moves to create the database from.
     * @return A [TestDatabase] with the given moves.
     */
    private fun createDataBaseFromMoves(moves: List<String>): TestDatabase {
      val testDataBase = TestDatabase()
      val game = Game()
      var previousMove: StoredMove? = null
      for (move in moves) {
        val currentPosition = game.position.toImmutablePosition()
        game.playMove(move)
        val storedMove = StoredMove(currentPosition, game.position.toImmutablePosition(), move)
        val node =
          StoredNode(
            currentPosition,
            previousMove?.let { mutableListOf(it) } ?: mutableListOf(),
            mutableListOf(storedMove),
          )
        previousMove = storedMove
        testDataBase.storedNodes[node.positionKey.fenRepresentation] = node
      }
      return testDataBase
    }

    /**
     * Merges multiple [TestDatabase] instances into one.
     *
     * @param testDataBases The databases to merge.
     * @return A new [TestDatabase] containing all positions and their moves.
     */
    fun merge(vararg testDataBases: TestDatabase): TestDatabase {
      val merged = TestDatabase()
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
                previousMoves.sortedBy { it.move }.toMutableList(),
                newMoves.sortedBy { it.move }.toMutableList(),
              )
          }
        }
      }
      return merged
    }
  }
}
