@file:OptIn(ExperimentalTime::class)

package proj.memorchess.axl.core.data.online.database

import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import proj.memorchess.axl.core.data.DataMove
import proj.memorchess.axl.core.data.DataPosition
import proj.memorchess.axl.core.data.PositionIdentifier
import proj.memorchess.axl.core.date.PreviousAndNextDate

internal const val IS_DELETED_FIELD = "is_deleted"
internal const val UPDATED_AT_FIELD = "updated_at"
internal const val DEPTH_FIELD = "depth"
internal const val NEXT_TRAINING_DATE_FIELD = "next_training_date"
internal const val LAST_TRAINING_DATE_FIELD = "last_training_date"

@Serializable
internal data class MoveFetched(
  val origin: String,
  val destination: String,
  val move: String,
  var isGood: Boolean?,
  val isDeleted: Boolean,
  val updatedAt: Instant,
) {
  constructor(
    dataMove: DataMove
  ) : this(
    dataMove.origin.fenRepresentation,
    dataMove.destination.fenRepresentation,
    dataMove.move,
    dataMove.isGood,
    dataMove.isDeleted,
    dataMove.updatedAt,
  )

  fun toStoredMove(): DataMove {
    return DataMove(
      PositionIdentifier(origin),
      PositionIdentifier(destination),
      move,
      isGood,
      isDeleted,
      updatedAt,
    )
  }
}

@Serializable
internal data class PositionFetched(
  val positionIdentifier: String,
  @SerialName("linked_moves") val linkedMoves: List<MoveFetched>,
  @SerialName(DEPTH_FIELD) val depth: Int,
  @SerialName(LAST_TRAINING_DATE_FIELD) val lastTrainingDate: LocalDate,
  @SerialName(NEXT_TRAINING_DATE_FIELD) val nextTrainingDate: LocalDate,
  @SerialName(UPDATED_AT_FIELD) val updatedAt: Instant,
  @SerialName(IS_DELETED_FIELD) val isDeleted: Boolean,
) {
  constructor(
    dataPosition: DataPosition,
    moves: List<DataMove>,
  ) : this(
    dataPosition.positionIdentifier.fenRepresentation,
    moves.map { MoveFetched(it) },
    dataPosition.depth,
    dataPosition.previousAndNextTrainingDate.previousDate,
    dataPosition.previousAndNextTrainingDate.nextDate,
    dataPosition.updatedAt,
    dataPosition.isDeleted,
  )

  fun toDataPosition(): DataPosition {
    return DataPosition(
      PositionIdentifier(positionIdentifier),
      depth,
      PreviousAndNextDate(lastTrainingDate, nextTrainingDate),
      updatedAt,
      isDeleted,
    )
  }

  fun toDataMoves(withDeletedOnes: Boolean = false): List<DataMove> {
    return linkedMoves
      .filter { withDeletedOnes || !it.isDeleted }
      .map { it.toStoredMove() }
  }
}

// Function arguments for Supabase RPC calls

@Serializable
internal data class SinglePositionFunctionArg(
  @SerialName("fen_representation_input") val fen: String
)

@Serializable
internal data class SingleDateTimeFunctionArg(@SerialName("hard_from_input") val hardFrom: Instant?)

@Serializable
internal data class MoveFromOriginFunctionArg(
  @SerialName("origin_input") val origin: String,
  @SerialName("move_input") val move: String,
)

@Serializable
internal data class InsertPositionFunctionArg(
  @SerialName("stored_nodes") val positions: List<PositionFetched>
)
