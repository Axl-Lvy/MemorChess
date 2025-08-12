package proj.memorchess.axl.test_util

import kotlinx.datetime.LocalDateTime
import proj.memorchess.axl.core.data.DatabaseQueryManager
import proj.memorchess.axl.core.data.PositionIdentifier
import proj.memorchess.axl.core.data.StoredMove
import proj.memorchess.axl.core.data.StoredNode
import proj.memorchess.axl.core.date.DateUtil
import proj.memorchess.axl.core.date.PreviousAndNextDate
import proj.memorchess.axl.core.engine.Game
import proj.memorchess.axl.core.graph.nodes.PreviousAndNextMoves

/**
 * A test in-memory database.
 *
 * @constructor Creates an empty test database.
 */
class TestDatabaseQueryManager private constructor() : DatabaseQueryManager {
  val storedNodes = mutableMapOf<String, StoredNode>()
  var isActiveState = true

  override suspend fun getAllNodes(withDeletedOnes: Boolean): List<StoredNode> {
    return storedNodes.values.filter { withDeletedOnes || !it.isDeleted }.toList()
  }

  override suspend fun getPosition(positionIdentifier: PositionIdentifier): StoredNode? {
    val node = storedNodes[positionIdentifier.fenRepresentation]
    return if (node?.isDeleted == false) {
      node
    } else {
      null
    }
  }

  override suspend fun deletePosition(position: PositionIdentifier) {
    val node = storedNodes[position.fenRepresentation]
    if (node != null) {
      storedNodes[position.fenRepresentation] =
        StoredNode(
          node.positionIdentifier,
          node.previousAndNextMoves,
          node.previousAndNextTrainingDate,
          DateUtil.now(),
          true,
        )
    }
  }

  override suspend fun deleteMove(origin: PositionIdentifier, move: String) {
    if (storedNodes[origin.fenRepresentation] == null) {
      return
    }
    val storedNode = storedNodes[origin.fenRepresentation]!!
    var destination: PositionIdentifier? = null
    val newNextMoves =
      storedNode.previousAndNextMoves.nextMoves.values.filter {
        return@filter if (it.move == move) {
          destination = it.destination
          false
        } else {
          true
        }
      }
    storedNodes[origin.fenRepresentation] =
      StoredNode(
        PositionIdentifier(origin.fenRepresentation),
        PreviousAndNextMoves(storedNode.previousAndNextMoves.previousMoves.values, newNextMoves),
        storedNode.previousAndNextTrainingDate,
        storedNode.updatedAt,
      )
    if (destination != null) {
      val destinationNode = storedNodes[destination.fenRepresentation]
      if (destinationNode != null) {
        val newPreviousMoves =
          destinationNode.previousAndNextMoves.previousMoves.values.filter { it.move != move }
        if (newPreviousMoves.isEmpty()) {
          storedNodes.remove(destination.fenRepresentation)
        } else {
          storedNodes[destination.fenRepresentation] =
            StoredNode(
              destinationNode.positionIdentifier,
              PreviousAndNextMoves(
                newPreviousMoves,
                destinationNode.previousAndNextMoves.nextMoves.values,
              ),
              destinationNode.previousAndNextTrainingDate,
              destinationNode.updatedAt,
            )
        }
      }
    }
  }

  override suspend fun deleteAll(hardFrom: LocalDateTime?) {
    storedNodes.clear()
  }

  override suspend fun insertNodes(vararg positions: StoredNode) {
    positions.forEach { storedNodes[it.positionIdentifier.fenRepresentation] = it }
  }

  override suspend fun getLastUpdate(): LocalDateTime? {
    val node = storedNodes.values.maxOfOrNull { it.updatedAt }
    val move =
      storedNodes.values
        .map { (it.previousAndNextMoves.previousMoves + it.previousAndNextMoves.nextMoves).values }
        .flatten()
        .maxOfOrNull { it.updatedAt }
    return DateUtil.maxOf(node, move)
  }

  override fun isActive(): Boolean {
    return isActiveState
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
