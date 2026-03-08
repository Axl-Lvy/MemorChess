@file:OptIn(ExperimentalTime::class)

package proj.memorchess.axl.core.data.online.database

import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import proj.memorchess.axl.core.data.DataMove
import proj.memorchess.axl.core.data.DataNode
import proj.memorchess.axl.core.data.PositionKey
import proj.memorchess.axl.core.date.PreviousAndNextDate
import proj.memorchess.axl.core.graph.PreviousAndNextMoves

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
  val isGood: Boolean?,
  val isDeleted: Boolean,
  val updatedAt: Instant,
) {
  constructor(
    dataMove: DataMove
  ) : this(
    origin = dataMove.origin.value,
    destination = dataMove.destination.value,
    move = dataMove.move,
    isGood = dataMove.isGood,
    isDeleted = dataMove.isDeleted,
    updatedAt = dataMove.updatedAt,
  )

  fun toStoredMove(): DataMove {
    return DataMove(
      PositionKey(origin),
      PositionKey(destination),
      move,
      isGood,
      isDeleted,
      updatedAt,
    )
  }
}

@Serializable
internal data class PositionFetched(
  @SerialName("positionIdentifier") val positionKey: String,
  @SerialName("linked_moves") val linkedMoves: List<MoveFetched>,
  @SerialName(DEPTH_FIELD) val depth: Int,
  @SerialName(LAST_TRAINING_DATE_FIELD) val lastTrainingDate: LocalDate,
  @SerialName(NEXT_TRAINING_DATE_FIELD) val nextTrainingDate: LocalDate,
  @SerialName(UPDATED_AT_FIELD) val updatedAt: Instant,
  @SerialName(IS_DELETED_FIELD) val isDeleted: Boolean,
) {
  constructor(
    dataNode: DataNode
  ) : this(
    dataNode.positionKey.value,
    dataNode.previousAndNextMoves.nextMoves.map { MoveFetched(it.value) } +
      dataNode.previousAndNextMoves.previousMoves.map { MoveFetched(it.value) },
    dataNode.depth,
    dataNode.previousAndNextTrainingDate.previousDate,
    dataNode.previousAndNextTrainingDate.nextDate,
    dataNode.updatedAt,
    dataNode.isDeleted,
  )

  fun toStoredNode(withDeletedOnes: Boolean = false): DataNode {
    return DataNode(
      PositionKey(positionKey),
      PreviousAndNextMoves(
        linkedMoves
          .filter { it.destination == positionKey && (withDeletedOnes || !it.isDeleted) }
          .map { it.toStoredMove() },
        linkedMoves
          .filter { it.origin == positionKey && (withDeletedOnes || !it.isDeleted) }
          .map { it.toStoredMove() },
      ),
      PreviousAndNextDate(lastTrainingDate, nextTrainingDate),
      depth,
      updatedAt,
    )
  }
}

// Function arguments for Supabase RPC calls

@Serializable
internal data class SinglePositionFunctionArg(
  @SerialName("fen_representation_input") val fen: String
) {
  companion object {
    fun from(positionKey: PositionKey) = SinglePositionFunctionArg(positionKey.value)
  }
}

@Serializable
internal data class SingleDateTimeFunctionArg(
  @SerialName("hard_from_input") val hardFrom: Instant?
)

@Serializable
internal data class MoveFromOriginFunctionArg(
  @SerialName("origin_input") val origin: String,
  @SerialName("move_input") val move: String,
) {
  companion object {
    fun from(origin: PositionKey, move: String) = MoveFromOriginFunctionArg(origin.value, move)
  }
}

@Serializable
internal data class InsertPositionFunctionArg(
  @SerialName("stored_nodes") val positions: List<PositionFetched>
)
