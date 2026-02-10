package proj.memorchess.axl.test_util

import kotlin.time.Instant
import proj.memorchess.axl.core.data.DataMove
import proj.memorchess.axl.core.data.DataPosition
import proj.memorchess.axl.core.data.DatabaseQueryManager
import proj.memorchess.axl.core.data.PositionIdentifier
import proj.memorchess.axl.core.date.DateUtil
import proj.memorchess.axl.core.date.PreviousAndNextDate
import proj.memorchess.axl.core.engine.Game

/**
 * A test in-memory database.
 *
 * @constructor Creates an empty test database.
 */
class TestDatabaseQueryManager private constructor() : DatabaseQueryManager {
  val dataMoves = mutableListOf<DataMove>()
  val dataPositions = mutableMapOf<String, DataPosition>()
  var isActiveState = true

  override suspend fun getAllMoves(withDeletedOnes: Boolean): List<DataMove> {
    return dataMoves.filter { withDeletedOnes || !it.isDeleted }
  }

  override suspend fun getAllPositions(withDeletedOnes: Boolean): List<DataPosition> {
    return dataPositions.values.filter { withDeletedOnes || !it.isDeleted }.toList()
  }

  override suspend fun getPosition(positionIdentifier: PositionIdentifier): DataPosition? {
    val position = dataPositions[positionIdentifier.fenRepresentation]
    return if (position?.isDeleted == false) position else null
  }

  override suspend fun getMovesForPosition(positionIdentifier: PositionIdentifier): List<DataMove> {
    val fen = positionIdentifier.fenRepresentation
    return dataMoves.filter {
      !it.isDeleted && (it.origin.fenRepresentation == fen || it.destination.fenRepresentation == fen)
    }
  }

  override suspend fun deletePosition(position: PositionIdentifier, updatedAt: Instant) {
    val existing = dataPositions[position.fenRepresentation]
    if (existing != null) {
      dataPositions[position.fenRepresentation] = existing.copy(isDeleted = true, updatedAt = DateUtil.now())
    }
  }

  override suspend fun deleteMove(origin: PositionIdentifier, move: String, updatedAt: Instant) {
    val index = dataMoves.indexOfFirst {
      !it.isDeleted && it.origin.fenRepresentation == origin.fenRepresentation && it.move == move
    }
    if (index >= 0) {
      val existing = dataMoves[index]
      dataMoves[index] = existing.copy(isDeleted = true, updatedAt = DateUtil.now())
    }
  }

  override suspend fun deleteAll(hardFrom: Instant?) {
    dataMoves.clear()
    dataPositions.clear()
  }

  override suspend fun insertMoves(moves: List<DataMove>, positions: List<DataPosition>) {
    for (position in positions) {
      dataPositions[position.positionIdentifier.fenRepresentation] = position
    }
    for (move in moves) {
      val existingIndex = dataMoves.indexOfFirst {
        it.origin == move.origin && it.destination == move.destination && it.move == move.move
      }
      if (existingIndex >= 0) {
        dataMoves[existingIndex] = move
      } else {
        dataMoves.add(move)
      }
    }
  }

  override suspend fun getLastUpdate(): Instant? {
    val positionUpdate = dataPositions.values.maxOfOrNull { it.updatedAt }
    val moveUpdate = dataMoves.maxOfOrNull { it.updatedAt }
    return DateUtil.maxOf(positionUpdate, moveUpdate)
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
      val (dataMoves, dataPositions) = convertStringMovesToMovesAndPositions(moves)
      testDataBase.dataMoves.addAll(dataMoves)
      for (position in dataPositions) {
        testDataBase.dataPositions[position.positionIdentifier.fenRepresentation] = position
      }
      return testDataBase
    }

    /**
     * Creates a minimal two-node chain: the starting position connected to the position after "e4".
     *
     * This is the smallest valid data unit — a position is only meaningful if it's connected to
     * another via a move.
     */
    fun minimalNodePair(): Pair<List<DataMove>, List<DataPosition>> {
      return convertStringMovesToMovesAndPositions(listOf("e4"))
    }

    fun convertStringMovesToMovesAndPositions(moves: List<String>): Pair<List<DataMove>, List<DataPosition>> {
      val dataMoves = mutableListOf<DataMove>()
      val dataPositions = mutableListOf<DataPosition>()
      val game = Game()
      for ((depth, move) in moves.withIndex()) {
        val currentPosition = game.position.createIdentifier()
        game.playMove(move)
        val dataMove = DataMove(currentPosition, game.position.createIdentifier(), move, true)
        dataMoves.add(dataMove)
        dataPositions.add(
          DataPosition(
            currentPosition,
            depth,
            PreviousAndNextDate.dummyToday(),
          )
        )
      }
      // Add the leaf position (final destination with no outgoing moves)
      dataPositions.add(
        DataPosition(
          game.position.createIdentifier(),
          moves.size,
          PreviousAndNextDate.dummyToday(),
        )
      )
      return Pair(dataMoves, dataPositions)
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
        for (position in dataBase.dataPositions.values) {
          merged.dataPositions[position.positionIdentifier.fenRepresentation] = position
        }
        for (move in dataBase.dataMoves) {
          val existingIndex = merged.dataMoves.indexOfFirst {
            it.origin == move.origin && it.destination == move.destination && it.move == move.move
          }
          if (existingIndex >= 0) {
            merged.dataMoves[existingIndex] = move
          } else {
            merged.dataMoves.add(move)
          }
        }
      }
      return merged
    }
  }
}
