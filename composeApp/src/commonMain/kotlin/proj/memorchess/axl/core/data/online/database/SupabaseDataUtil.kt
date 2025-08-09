package proj.memorchess.axl.core.data.online.database

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.PostgrestQueryBuilder
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import proj.memorchess.axl.core.data.PositionIdentifier
import proj.memorchess.axl.core.data.StoredMove
import proj.memorchess.axl.core.data.StoredNode
import proj.memorchess.axl.core.date.PreviousAndNextDate
import proj.memorchess.axl.core.graph.nodes.PreviousAndNextMoves

// Fields name
internal const val POSITION_ID_FIELD = "position_id"
internal const val FEN_REPRESENTATION_FIELD = "fen_representation"
internal const val ID_FIELD = "id"
internal const val ORIGIN_FIELD = "origin"
internal const val DESTINATION_FIELD = "destination"
internal const val NAME_FIELD = "name"
internal const val MOVE_ID_FIELD = "move_id"
internal const val USER_ID_FIELD = "user_id"
internal const val IS_DELETED_FIELD = "is_deleted"
internal const val UPDATED_AT_FIELD = "updated_at"
internal const val DEPTH_FIELD = "depth"
internal const val NEXT_TRAINING_DATE_FIELD = "next_training_date"
internal const val LAST_TRAINING_DATE_FIELD = "last_training_date"

// Tables name
private const val POSITION_TABLE = "Position"
private const val USER_POSITION_TABLE = "UserPosition"
private const val MOVE_TABLE = "Move"
private const val USER_MOVE_TABLE = "UserMove"

internal fun SupabaseClient.from(table: Table): PostgrestQueryBuilder {
  return this.from(table.tableName)
}

internal enum class Table(val tableName: String) {
  POSITION(POSITION_TABLE),
  USER_POSITION(USER_POSITION_TABLE),
  MOVE(MOVE_TABLE),
  USER_MOVE(USER_MOVE_TABLE),
}

@Serializable
internal data class RemotePosition(
  @SerialName(ID_FIELD) val id: Long,
  @SerialName(FEN_REPRESENTATION_FIELD) val fenRepresentation: String,
)

@Serializable
internal data class RemoteMove(
  @SerialName(ID_FIELD) val id: Long,
  @SerialName(ORIGIN_FIELD) val origin: Long,
  @SerialName(DESTINATION_FIELD) val destination: Long,
  @SerialName(NAME_FIELD) val name: String,
)

@Serializable
internal data class MoveToUpload(
  val origin: String,
  val destination: String,
  val move: String,
  var isGood: Boolean?,
  val isDeleted: Boolean,
  val updatedAt: LocalDateTime,
) {
  constructor(
    storedMove: StoredMove
  ) : this(
    storedMove.origin.fenRepresentation,
    storedMove.destination.fenRepresentation,
    storedMove.move,
    storedMove.isGood,
    storedMove.isDeleted,
    storedMove.updatedAt,
  )

  fun toStoredMove(): StoredMove {
    return StoredMove(
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
internal data class PositionToUpload(
  val positionIdentifier: String,
  @SerialName("linked_moves") val linkedMoves: List<MoveToUpload>,
  @SerialName(DEPTH_FIELD) val depth: Int,
  @SerialName(LAST_TRAINING_DATE_FIELD) val lastTrainingDate: LocalDate,
  @SerialName(NEXT_TRAINING_DATE_FIELD) val nextTrainingDate: LocalDate,
  @SerialName(UPDATED_AT_FIELD) val updatedAt: LocalDateTime,
  @SerialName(IS_DELETED_FIELD) val isDeleted: Boolean,
) {
  constructor(
    storedNode: StoredNode
  ) : this(
    storedNode.positionIdentifier.fenRepresentation,
    storedNode.previousAndNextMoves.nextMoves.map { MoveToUpload(it.value) } +
      storedNode.previousAndNextMoves.previousMoves.map { MoveToUpload(it.value) },
    storedNode.previousAndNextMoves.depth,
    storedNode.previousAndNextTrainingDate.previousDate,
    storedNode.previousAndNextTrainingDate.nextDate,
    storedNode.updatedAt,
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
      updatedAt,
    )
  }
}

@Serializable
internal data class FetchSinglePositionFunctionArg(
  @SerialName("user_id_input") val userId: String,
  @SerialName("fen_representation_input") val fen: String,
)

@Serializable
internal data class InsertPositionFunctionArg(
  @SerialName("user_id_input") val userId: String,
  @SerialName("stored_nodes") val positions: List<PositionToUpload>,
)

@Serializable
internal data class SingleUserIdInput(@SerialName("user_id_input") val userId: String)

@Serializable
internal data class SingleUpdatedAtTime(@SerialName(UPDATED_AT_FIELD) val updatedAt: LocalDateTime)
