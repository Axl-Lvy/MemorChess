@file:OptIn(ExperimentalTime::class)

package proj.memorchess.axl.core.data.online.database

import kotlin.time.ExperimentalTime
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import proj.memorchess.axl.core.data.PositionIdentifier
import proj.memorchess.axl.core.data.StoredMove
import proj.memorchess.axl.core.data.StoredNode
import proj.memorchess.axl.core.date.PreviousAndNextDate
import proj.memorchess.axl.core.graph.nodes.PreviousAndNextMoves

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
    storedMove: StoredMove
  ) : this(
    storedMove.origin.fenRepresentation,
    storedMove.destination.fenRepresentation,
    storedMove.move,
    storedMove.isGood,
    storedMove.isDeleted,
    storedMove.updatedAt.toInstant(TimeZone.currentSystemDefault()),
  )

  fun toStoredMove(): StoredMove {
    return StoredMove(
      PositionIdentifier(origin),
      PositionIdentifier(destination),
      move,
      isGood,
      isDeleted,
      updatedAt.toLocalDateTime(TimeZone.currentSystemDefault()),
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
    storedNode: StoredNode
  ) : this(
    storedNode.positionIdentifier.fenRepresentation,
    storedNode.previousAndNextMoves.nextMoves.map { MoveFetched(it.value) } +
      storedNode.previousAndNextMoves.previousMoves.map { MoveFetched(it.value) },
    storedNode.previousAndNextMoves.depth,
    storedNode.previousAndNextTrainingDate.previousDate,
    storedNode.previousAndNextTrainingDate.nextDate,
    storedNode.updatedAt.toInstant(TimeZone.currentSystemDefault()),
    storedNode.isDeleted,
  )

  fun toStoredNode(withDeletedOnes: Boolean = false): StoredNode {
    return StoredNode(
      PositionIdentifier(positionIdentifier),
      PreviousAndNextMoves(
        linkedMoves
          .filter { it.destination == positionIdentifier && (withDeletedOnes || !it.isDeleted) }
          .map { it.toStoredMove() },
        linkedMoves
          .filter { it.origin == positionIdentifier && (withDeletedOnes || !it.isDeleted) }
          .map { it.toStoredMove() },
        depth,
      ),
      PreviousAndNextDate(lastTrainingDate, nextTrainingDate),
      updatedAt.toLocalDateTime(TimeZone.currentSystemDefault()),
    )
  }
}

// Function arguments for Supabase RPC calls

@Serializable
internal data class SinglePositionFunctionArg(
  @SerialName("user_id_input") val userId: String,
  @SerialName("fen_representation_input") val fen: String,
)

@Serializable
internal data class SingleDateTimeFunctionArg(
  @SerialName("user_id_input") val userId: String,
  @SerialName("hard_from_input") val hardFrom: Instant?,
)

@Serializable
internal data class MoveFromOriginFunctionArg(
  @SerialName("user_id_input") val userId: String,
  @SerialName("origin_input") val origin: String,
  @SerialName("move_input") val move: String,
)

@Serializable
internal data class InsertPositionFunctionArg(
  @SerialName("user_id_input") val userId: String,
  @SerialName("stored_nodes") val positions: List<PositionFetched>,
)

@Serializable
internal data class SingleUserIdFunctionArg(@SerialName("user_id_input") val userId: String)
