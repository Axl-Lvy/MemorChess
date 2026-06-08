package proj.memorchess.axl.test_util

import kotlin.time.Instant
import proj.memorchess.axl.core.data.DataMove
import proj.memorchess.axl.core.data.DataNode
import proj.memorchess.axl.core.data.DatabaseQueryManager
import proj.memorchess.axl.core.data.PositionKey
import proj.memorchess.axl.core.date.DateUtil
import proj.memorchess.axl.core.engine.GameEngine
import proj.memorchess.axl.core.graph.DeleteMode
import proj.memorchess.axl.core.graph.PreviousAndNextMoves
import proj.memorchess.axl.core.scheduling.CardStateFactory

/**
 * Test in memory database. Mirrors the behaviour of the real platform implementations: hard deletes
 * physically remove rows, soft deletes flip the [DataNode.isDeleted] flag.
 *
 * @constructor Creates an empty test database.
 */
class TestDatabaseQueryManager private constructor() : DatabaseQueryManager {
  val dataNodes = mutableMapOf<PositionKey, DataNode>()

  override suspend fun getAllNodes(withDeletedOnes: Boolean): List<DataNode> {
    return dataNodes.values.filter { withDeletedOnes || !it.isDeleted }.toList()
  }

  override suspend fun getPosition(positionKey: PositionKey): DataNode? {
    val node = dataNodes[positionKey]
    return if (node?.isDeleted == false) node else null
  }

  override suspend fun deletePosition(position: PositionKey, mode: DeleteMode) {
    val node = dataNodes[position] ?: return
    when (mode) {
      DeleteMode.HARD -> {
        dataNodes.remove(position)
        // Hard delete also drops incident edges from other nodes' move maps.
        for ((key, other) in dataNodes.toMap()) {
          val nextMoves =
            other.previousAndNextMoves.nextMoves.values.filter { it.destination != position }
          val previousMoves =
            other.previousAndNextMoves.previousMoves.values.filter { it.origin != position }
          if (
            nextMoves.size != other.previousAndNextMoves.nextMoves.size ||
              previousMoves.size != other.previousAndNextMoves.previousMoves.size
          ) {
            dataNodes[key] =
              DataNode(
                other.positionKey,
                PreviousAndNextMoves(previousMoves, nextMoves),
                other.cardState,
                other.depth,
                other.updatedAt,
                other.isDeleted,
              )
          }
        }
      }
      DeleteMode.SOFT ->
        dataNodes[position] =
          DataNode(
            node.positionKey,
            node.previousAndNextMoves,
            node.cardState,
            node.depth,
            DateUtil.now(),
            true,
          )
    }
  }

  override suspend fun deleteMove(origin: PositionKey, move: String, mode: DeleteMode) {
    val storedNode = dataNodes[origin] ?: return
    var destination: PositionKey? = null
    val newNextMoves =
      storedNode.previousAndNextMoves.nextMoves.values.mapNotNull {
        if (it.move != move) it
        else {
          destination = it.destination
          when (mode) {
            DeleteMode.HARD -> null
            DeleteMode.SOFT -> it.copy(isDeleted = true, updatedAt = DateUtil.now())
          }
        }
      }
    dataNodes[origin] =
      DataNode(
        origin,
        PreviousAndNextMoves(storedNode.previousAndNextMoves.previousMoves.values, newNextMoves),
        storedNode.cardState,
        storedNode.depth,
        storedNode.updatedAt,
      )
    val dest = destination ?: return
    val destinationNode = dataNodes[dest] ?: return
    val newPreviousMoves =
      destinationNode.previousAndNextMoves.previousMoves.values.mapNotNull {
        if (it.move != move) it
        else
          when (mode) {
            DeleteMode.HARD -> null
            DeleteMode.SOFT -> it.copy(isDeleted = true, updatedAt = DateUtil.now())
          }
      }
    dataNodes[dest] =
      DataNode(
        destinationNode.positionKey,
        PreviousAndNextMoves(
          newPreviousMoves,
          destinationNode.previousAndNextMoves.nextMoves.values,
        ),
        destinationNode.cardState,
        destinationNode.depth,
        destinationNode.updatedAt,
      )
  }

  override suspend fun eraseAll() {
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
    return when {
      node == null -> move
      move == null -> node
      else -> maxOf(node, move)
    }
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
