package proj.memorchess.axl.test_util

import proj.memorchess.axl.core.data.ICommonDatabase
import proj.memorchess.axl.core.data.IStoredNode
import proj.memorchess.axl.core.data.PositionKey
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
class TestDatabase private constructor() : ICommonDatabase {
  val storedNodes = mutableMapOf<String, StoredNode>()

  override suspend fun getAllPositions(): List<StoredNode> {
    return storedNodes.values.toList()
  }

  override suspend fun getPosition(positionKey: PositionKey): StoredNode? {
    return storedNodes[positionKey.fenRepresentation]
  }

  override suspend fun deletePosition(fen: String) {
    storedNodes.remove(fen)
  }

  override suspend fun deleteMoveFrom(origin: String) {
    if (storedNodes[origin] == null) {
      return
    }
    storedNodes[origin] =
      StoredNode(
        PositionKey(origin),
        PreviousAndNextMoves(
          storedNodes[origin]!!.previousAndNextMoves.previousMoves.values,
          listOf(),
        ),
        storedNodes[origin]!!.previousAndNextTrainingDate,
      )
  }

  override suspend fun deleteMoveTo(destination: String) {
    if (storedNodes[destination] == null) {
      return
    }
    storedNodes[destination] =
      StoredNode(
        PositionKey(destination),
        PreviousAndNextMoves(
          listOf(),
          storedNodes[destination]!!.previousAndNextMoves.nextMoves.values,
        ),
        storedNodes[destination]!!.previousAndNextTrainingDate,
      )
  }

  override suspend fun deleteMove(origin: String, move: String) {
    if (storedNodes[origin] == null) {
      return
    }
    val storedNode = storedNodes[origin]!!
    val newNextMoves = storedNode.previousAndNextMoves.nextMoves.values.filter { it.move != move }
    storedNodes[origin] =
      StoredNode(
        PositionKey(origin),
        PreviousAndNextMoves(storedNode.previousAndNextMoves.previousMoves.values, newNextMoves),
        storedNode.previousAndNextTrainingDate,
      )
  }

  override suspend fun insertMove(move: StoredMove) {
    storedNodes[move.origin.fenRepresentation]!!.previousAndNextMoves.nextMoves[move.move] = move
    storedNodes[move.destination.fenRepresentation]!!
      .previousAndNextMoves
      .previousMoves[move.move] = move
  }

  override suspend fun getAllMoves(): List<StoredMove> {
    return storedNodes.values
      .flatMap {
        it.previousAndNextMoves.nextMoves.values + it.previousAndNextMoves.previousMoves.values
      }
      .distinct()
  }

  override suspend fun deleteAllMoves() {
    storedNodes.values.forEach {
      it.previousAndNextMoves.nextMoves.clear()
      it.previousAndNextMoves.previousMoves.clear()
    }
  }

  override suspend fun insertPosition(position: IStoredNode) {
    storedNodes[position.positionKey.fenRepresentation] = position as StoredNode
  }

  override suspend fun deleteAllNodes() {
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
      val nodes = convertStringMovesToNodes(moves)
      for (node in nodes) {
        testDataBase.storedNodes[node.positionKey.fenRepresentation] = node
      }
      return testDataBase
    }

    fun convertStringMovesToNodes(moves: List<String>): List<StoredNode> {
      val nodes = mutableListOf<StoredNode>()
      val game = Game()
      var previousMove: StoredMove? = null
      var depth = 0
      for (move in moves) {
        val currentPosition = game.position.toImmutablePosition()
        game.playMove(move)
        val storedMove =
          StoredMove(currentPosition, game.position.toImmutablePosition(), move, true)
        val node =
          StoredNode(
            currentPosition,
            PreviousAndNextMoves(
              previousMove?.let { listOf(it) } ?: listOf(),
              listOf(storedMove),
              depth++,
            ),
            PreviousAndNextDate.dummyToday(),
          )
        previousMove = storedMove
        nodes.add(node)
      }
      return nodes
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
            val newMoves = storedNode.previousAndNextMoves.nextMoves.values.toMutableSet()
            newMoves.addAll(entry.value.previousAndNextMoves.nextMoves.values)
            val previousMoves = storedNode.previousAndNextMoves.previousMoves.values.toMutableSet()
            newMoves.addAll(entry.value.previousAndNextMoves.previousMoves.values)
            merged.storedNodes[entry.key] =
              StoredNode(
                storedNode.positionKey,
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
