package proj.memorchess.axl.core.graph.nodes

import co.touchlab.kermit.Logger
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import proj.memorchess.axl.core.data.DataMove
import proj.memorchess.axl.core.data.DataPosition
import proj.memorchess.axl.core.data.DatabaseQueryManager
import proj.memorchess.axl.core.data.PositionIdentifier
import proj.memorchess.axl.core.date.DateUtil

/**
 * DbBasedNodeCache is a NodeCache implementation that uses a database as the source of truth for
 * positions and moves.
 */
class DbBasedNodeCache : NodeCache(), KoinComponent {

  private val database by inject<DatabaseQueryManager>()

  private val positionsByDay = mutableMapOf<Int, MutableMap<PositionIdentifier, DataPosition>>()

  override suspend fun deleteMove(move: DataMove) {
    database.deleteMove(move.origin, move.move)
  }

  override fun cachePosition(position: DataPosition) {
    val moves = movesCache[position.positionIdentifier]
    if (moves != null && moves.depth > position.depth) {
      moves.depth = position.depth
    }
    val daysUntil = DateUtil.daysUntil(position.previousAndNextTrainingDate.nextDate)
    positionsByDay.getOrPut(daysUntil) { mutableMapOf() }[position.positionIdentifier] = position
  }

  override fun getPositionFromDay(day: Int): DataPosition? {
    val candidates = positionsByDay[day] ?: return null
    val positionId =
      candidates.entries.minByOrNull {
        movesCache[it.key]?.depth ?: Int.MAX_VALUE
      }?.key ?: return null
    return candidates.remove(positionId)
  }

  override fun getPositionToTrainAfterPosition(
    day: Int,
    positionIdentifier: PositionIdentifier,
  ): DataPosition? {
    val todayPositions = positionsByDay[day] ?: return null
    for (candidatePosition in
      movesCache[positionIdentifier]?.nextMoves?.values?.map { it.destination } ?: emptyList()) {
      val candidateDataPosition = todayPositions.remove(candidatePosition)
      if (candidateDataPosition != null) {
        return candidateDataPosition
      }
    }
    return null
  }

  override fun getNumberOfPositionsToTrain(day: Int): Int {
    return positionsByDay[day]?.size ?: 0
  }

  override suspend fun resetFromSource() {
    clear()
    val allMoves: List<DataMove> = database.getAllMoves()
    val allPositions: List<DataPosition> = database.getAllPositions()

    // Build moves cache from flat move list
    for (move in allMoves) {
      movesCache
        .getOrPut(move.origin) { PreviousAndNextMoves() }
        .addNextMove(move)
      movesCache
        .getOrPut(move.destination) { PreviousAndNextMoves() }
        .addPreviousMove(move)
    }

    // Cache positions for training scheduling
    for (position in allPositions) {
      val moves = movesCache[position.positionIdentifier]
      if (moves != null) {
        moves.depth = position.depth
        if (moves.nextMoves.any { it.value.isGood == true }) {
          val daysUntil = DateUtil.daysUntil(position.previousAndNextTrainingDate.nextDate)
          positionsByDay.getOrPut(daysUntil) { mutableMapOf() }[position.positionIdentifier] = position
        }
      }
      LOGGER.i { "Retrieved position: ${position.positionIdentifier}" }
    }
  }

  /** Clears the cache and resets the database retrieved flag. */
  private fun clear() {
    positionsByDay.clear()
    movesCache.clear()
  }
}

private val LOGGER = Logger.withTag("DbBasedNodeCache")
