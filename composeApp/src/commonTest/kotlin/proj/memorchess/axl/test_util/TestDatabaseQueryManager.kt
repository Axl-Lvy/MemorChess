package proj.memorchess.axl.test_util

import kotlinx.datetime.LocalDateTime
import proj.memorchess.axl.core.data.DataMove
import proj.memorchess.axl.core.data.DataNode
import proj.memorchess.axl.core.data.DatabaseQueryManager
import proj.memorchess.axl.core.data.PositionIdentifier
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
  val dataNodes = mutableMapOf<String, DataNode>()
  var isActiveState = true

  override suspend fun getAllNodes(withDeletedOnes: Boolean): List<DataNode> {
    return dataNodes.values.filter { withDeletedOnes || !it.isDeleted }.toList()
  }

  override suspend fun getPosition(positionIdentifier: PositionIdentifier): DataNode? {
    val node = dataNodes[positionIdentifier.fenRepresentation]
    return if (node?.isDeleted == false) {
      node
    } else {
      null
    }
  }

  override suspend fun deletePosition(position: PositionIdentifier) {
    val node = dataNodes[position.fenRepresentation]
    if (node != null) {
      dataNodes[position.fenRepresentation] =
        DataNode(
          node.positionIdentifier,
          node.previousAndNextMoves,
          node.previousAndNextTrainingDate,
          DateUtil.now(),
          true,
        )
    }
  }

  override suspend fun deleteMove(origin: PositionIdentifier, move: String) {
    if (dataNodes[origin.fenRepresentation] == null) {
      return
    }
    val storedNode = dataNodes[origin.fenRepresentation]!!
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
    dataNodes[origin.fenRepresentation] =
      DataNode(
        PositionIdentifier(origin.fenRepresentation),
        PreviousAndNextMoves(storedNode.previousAndNextMoves.previousMoves.values, newNextMoves),
        storedNode.previousAndNextTrainingDate,
        storedNode.updatedAt,
      )
    if (destination != null) {
      val destinationNode = dataNodes[destination.fenRepresentation]
      if (destinationNode != null) {
        val newPreviousMoves =
          destinationNode.previousAndNextMoves.previousMoves.values.filter { it.move != move }
        if (newPreviousMoves.isEmpty()) {
          dataNodes.remove(destination.fenRepresentation)
        } else {
          dataNodes[destination.fenRepresentation] =
            DataNode(
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
    dataNodes.clear()
  }

  override suspend fun insertNodes(vararg positions: DataNode) {
    positions.forEach { dataNodes[it.positionIdentifier.fenRepresentation] = it }
  }

  override suspend fun getLastUpdate(): LocalDateTime? {
    val node = dataNodes.values.maxOfOrNull { it.updatedAt }
    val move =
      dataNodes.values
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
        testDataBase.dataNodes[node.positionIdentifier.fenRepresentation] = node
      }
      return testDataBase
    }

    fun convertStringMovesToNodes(moves: List<String>): List<DataNode> {
      val nodes = mutableListOf<DataNode>()
      val game = Game()
      var previousMove: DataMove? = null
      for ((depth, move) in moves.withIndex()) {
        val currentPosition = game.position.createIdentifier()
        game.playMove(move)
        val dataMove = DataMove(currentPosition, game.position.createIdentifier(), move, true)
        val node =
          DataNode(
            currentPosition,
            PreviousAndNextMoves(
              previousMove?.let { listOf(it) } ?: listOf(),
              listOf(dataMove),
              depth,
            ),
            PreviousAndNextDate.dummyToday(),
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
        for (entry in dataBase.dataNodes) {
          val storedNode = merged.dataNodes[entry.key]
          if (storedNode == null) {
            merged.dataNodes[entry.key] = entry.value
          } else {
            val newMoves = storedNode.previousAndNextMoves.nextMoves.values.toMutableSet()
            newMoves.addAll(entry.value.previousAndNextMoves.nextMoves.values)
            val previousMoves = storedNode.previousAndNextMoves.previousMoves.values.toMutableSet()
            newMoves.addAll(entry.value.previousAndNextMoves.previousMoves.values)
            merged.dataNodes[entry.key] =
              DataNode(
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
