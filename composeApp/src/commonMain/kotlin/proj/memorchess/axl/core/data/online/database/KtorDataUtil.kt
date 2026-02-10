package proj.memorchess.axl.core.data.online.database

import proj.memorchess.axl.core.data.DataMove
import proj.memorchess.axl.core.data.DataPosition
import proj.memorchess.axl.core.data.PositionIdentifier
import proj.memorchess.axl.core.date.PreviousAndNextDate
import proj.memorchess.axl.shared.data.MoveFetched
import proj.memorchess.axl.shared.data.NodeFetched
import proj.memorchess.axl.shared.data.PositionFetched

/** Converts a [MoveFetched] from the server into a [DataMove]. */
internal fun MoveFetched.toDataMove(): DataMove {
  return DataMove(
    origin = PositionIdentifier(origin.positionIdentifier),
    destination = PositionIdentifier(destination.positionIdentifier),
    move = move,
    isGood = isGood,
    isDeleted = isDeleted,
    updatedAt = updatedAt,
  )
}

/** Converts a [PositionFetched] from the server into a [DataPosition]. */
internal fun PositionFetched.toDataPosition(): DataPosition {
  return DataPosition(
    positionIdentifier = PositionIdentifier(positionIdentifier),
    depth = depth,
    previousAndNextTrainingDate = PreviousAndNextDate(lastTrainingDate, nextTrainingDate),
    updatedAt = updatedAt,
    isDeleted = isDeleted,
  )
}

/** Converts a [NodeFetched] from the server into a [DataPosition] and its associated [DataMove]s. */
internal fun NodeFetched.toDataPositionAndMoves(): Pair<DataPosition, List<DataMove>> {
  return Pair(position.toDataPosition(), moves.map { it.toDataMove() })
}

/**
 * Converts a flat list of [MoveFetched] into separate lists of [DataMove] and [DataPosition].
 *
 * Each position appearing as origin or destination in the moves list produces a [DataPosition].
 */
internal fun moveFetchedToMovesAndPositions(
  moves: List<MoveFetched>,
  withDeletedOnes: Boolean = false,
): Pair<List<DataMove>, List<DataPosition>> {
  val positionMap = mutableMapOf<String, PositionFetched>()

  for (move in moves) {
    positionMap.getOrPut(move.origin.positionIdentifier) { move.origin }
    positionMap.getOrPut(move.destination.positionIdentifier) { move.destination }
  }

  val dataMoves = moves.map { it.toDataMove() }
  val dataPositions = positionMap.values
    .filter { withDeletedOnes || !it.isDeleted }
    .map { it.toDataPosition() }

  return Pair(dataMoves, dataPositions)
}

/**
 * Converts lists of [DataMove] and [DataPosition] into a list of [MoveFetched] suitable for the
 * server's POST endpoint.
 */
internal fun dataMovesToMoveFetched(
  moves: List<DataMove>,
  positions: List<DataPosition>,
): List<MoveFetched> {
  val positionLookup = positions.associateBy { it.positionIdentifier.fenRepresentation }
  return moves.map { dataMove ->
    val originPosition = positionLookup[dataMove.origin.fenRepresentation]
    val destPosition = positionLookup[dataMove.destination.fenRepresentation]
    MoveFetched(
      origin = originPosition?.toPositionFetched()
        ?: dataMove.origin.toDefaultPositionFetched(dataMove),
      destination = destPosition?.toPositionFetched()
        ?: dataMove.destination.toDefaultPositionFetched(dataMove),
      move = dataMove.move,
      isGood = dataMove.isGood ?: true,
      isDeleted = dataMove.isDeleted,
      updatedAt = dataMove.updatedAt,
    )
  }
}

private fun DataPosition.toPositionFetched(): PositionFetched {
  return PositionFetched(
    positionIdentifier = positionIdentifier.fenRepresentation,
    depth = depth,
    lastTrainingDate = previousAndNextTrainingDate.previousDate,
    nextTrainingDate = previousAndNextTrainingDate.nextDate,
    updatedAt = updatedAt,
    isDeleted = isDeleted,
  )
}

private fun PositionIdentifier.toDefaultPositionFetched(move: DataMove): PositionFetched {
  return PositionFetched(
    positionIdentifier = fenRepresentation,
    depth = 0,
    lastTrainingDate = PreviousAndNextDate.dummyToday().previousDate,
    nextTrainingDate = PreviousAndNextDate.dummyToday().nextDate,
    updatedAt = move.updatedAt,
    isDeleted = move.isDeleted,
  )
}
