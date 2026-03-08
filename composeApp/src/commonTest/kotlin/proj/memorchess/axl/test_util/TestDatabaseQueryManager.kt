package proj.memorchess.axl.test_util

import kotlin.time.Instant
import proj.memorchess.axl.core.data.DataMove
import proj.memorchess.axl.core.data.DataNode
import proj.memorchess.axl.core.data.DatabaseQueryManager
import proj.memorchess.axl.core.data.PositionKey
import proj.memorchess.axl.core.date.DateUtil
import proj.memorchess.axl.core.date.PreviousAndNextDate
import proj.memorchess.axl.core.engine.GameEngine
import proj.memorchess.axl.core.graph.PreviousAndNextMoves

/**
 * A test in-memory database.
 *
 * @constructor Creates an empty test database.
 */
class TestDatabaseQueryManager private constructor() : DatabaseQueryManager {
  val dataNodes = mutableMapOf<PositionKey, DataNode>()
  var isActiveState = true

  override suspend fun getAllNodes(withDeletedOnes: Boolean): List<DataNode> {
    return dataNodes.values.filter { withDeletedOnes || !it.isDeleted }.toList()
  }

  override suspend fun getPosition(positionKey: PositionKey): DataNode? {
    val node = dataNodes[positionKey]
    return if (node?.isDeleted == false) {
      node
    } else {
      null
    }
  }

  override suspend fun deletePosition(position: PositionKey) {
    val node = dataNodes[position]
    if (node != null) {
      dataNodes[position] =
        DataNode(
          node.positionKey,
          node.previousAndNextMoves,
          node.previousAndNextTrainingDate,
          DateUtil.now(),
          true,
        )
    }
  }

  override suspend fun deleteMove(origin: PositionKey, move: String) {
    val storedNode = dataNodes[origin] ?: return
    var destination: PositionKey? = null
    val newNextMoves =
      storedNode.previousAndNextMoves.nextMoves.values.filter {
        return@filter if (it.move == move) {
          destination = it.destination
          false
        } else {
          true
        }
      }
    dataNodes[origin] =
      DataNode(
        origin,
        PreviousAndNextMoves(storedNode.previousAndNextMoves.previousMoves.values, newNextMoves),
        storedNode.previousAndNextTrainingDate,
        storedNode.updatedAt,
      )
    val dest = destination ?: return
    val destinationNode = dataNodes[dest] ?: return
    val newPreviousMoves =
      destinationNode.previousAndNextMoves.previousMoves.values.filter { it.move != move }
    if (newPreviousMoves.isEmpty()) {
      dataNodes.remove(dest)
    } else {
      dataNodes[dest] =
        DataNode(
          destinationNode.positionKey,
          PreviousAndNextMoves(
            newPreviousMoves,
            destinationNode.previousAndNextMoves.nextMoves.values,
          ),
          destinationNode.previousAndNextTrainingDate,
          destinationNode.updatedAt,
        )
    }
  }

  override suspend fun deleteAll(hardFrom: Instant?) {
    dataNodes.clear()
  }

  override suspend fun insertNodes(vararg positions: DataNode) {
    positions.forEach { dataNodes[it.positionKey] = it }
  }

  override suspend fun getLastUpdate(): Instant? {
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
                PreviousAndNextDate.dummyToday(),
              )
          }
        }
      }
      return merged
    }
  }
}
