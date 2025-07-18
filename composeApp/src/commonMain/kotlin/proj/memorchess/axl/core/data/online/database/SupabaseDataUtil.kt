package proj.memorchess.axl.core.data.online.database

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.PostgrestQueryBuilder
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Fields name
internal const val POSITION_ID_FIELD = "position_id"
internal const val FEN_REPRESENTATION_FIELD = "fen_representation"
internal const val ID_FIELD = "id"
internal const val ORIGIN_FIELD = "origin"
internal const val DESTINATION_FIELD = "destination"
internal const val NAME_FIELD = "name"
internal const val MOVE_ID_FIELD = "move_id"
internal const val IS_GOOD_FIELD = "is_good"
internal const val CREATED_AT_FIELD = "created_at"
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
internal data class RemotePositionToUpload(
  @SerialName(FEN_REPRESENTATION_FIELD) val fenRepresentation: String
)

@Serializable
internal data class RemoteMove(
  @SerialName(ID_FIELD) val id: Long,
  @SerialName(ORIGIN_FIELD) val origin: Long,
  @SerialName(DESTINATION_FIELD) val destination: Long,
  @SerialName(NAME_FIELD) val name: String,
)

@Serializable
internal data class RemoteMoveToUpload(
  @SerialName(ORIGIN_FIELD) val origin: Long,
  @SerialName(DESTINATION_FIELD) val destination: Long,
  @SerialName(NAME_FIELD) val name: String,
)

@Serializable
internal data class RemoteUserMove(
  @SerialName(ID_FIELD) val id: Long,
  @SerialName(MOVE_ID_FIELD) val moveId: Long,
  @SerialName(IS_GOOD_FIELD) val isGood: Boolean,
  @SerialName(CREATED_AT_FIELD) val createdAt: LocalDateTime,
  @SerialName(USER_ID_FIELD) val userId: String,
  @SerialName(IS_DELETED_FIELD) val isDeleted: Boolean,
  @SerialName(UPDATED_AT_FIELD) val updatedAt: LocalDateTime,
)

@Serializable
internal data class RemoteUserMoveToUpload(
  @SerialName(MOVE_ID_FIELD) val moveId: Long,
  @SerialName(IS_GOOD_FIELD) val isGood: Boolean,
  @SerialName(CREATED_AT_FIELD) val createdAt: LocalDateTime,
  @SerialName(USER_ID_FIELD) val userId: String,
  @SerialName(IS_DELETED_FIELD) val isDeleted: Boolean,
  @SerialName(UPDATED_AT_FIELD) val updatedAt: LocalDateTime,
)

@Serializable
internal data class RemoteUserPosition(
  @SerialName(ID_FIELD) val id: Long,
  @SerialName(USER_ID_FIELD) val userId: String,
  @SerialName(POSITION_ID_FIELD) val positionId: Long,
  @SerialName(DEPTH_FIELD) val depth: Int,
  @SerialName(NEXT_TRAINING_DATE_FIELD) val nextTrainingDate: LocalDate,
  @SerialName(LAST_TRAINING_DATE_FIELD) val lastTrainingDate: LocalDate,
  @SerialName(CREATED_AT_FIELD) val createdAt: LocalDateTime,
  @SerialName(IS_DELETED_FIELD) val isDeleted: Boolean,
  @SerialName(UPDATED_AT_FIELD) val updatedAt: LocalDateTime,
)

@Serializable
internal data class RemoteUserPositionToUpload(
  @SerialName(USER_ID_FIELD) val userId: String,
  @SerialName(POSITION_ID_FIELD) val positionId: Long,
  @SerialName(DEPTH_FIELD) val depth: Int,
  @SerialName(NEXT_TRAINING_DATE_FIELD) val nextTrainingDate: LocalDate,
  @SerialName(LAST_TRAINING_DATE_FIELD) val lastTrainingDate: LocalDate,
  @SerialName(CREATED_AT_FIELD) val createdAt: LocalDateTime,
  @SerialName(IS_DELETED_FIELD) val isDeleted: Boolean,
  @SerialName(UPDATED_AT_FIELD) val updatedAt: LocalDateTime,
)

@Serializable
internal data class SingleUpdatedAtTime(@SerialName(UPDATED_AT_FIELD) val updatedAt: LocalDateTime)
