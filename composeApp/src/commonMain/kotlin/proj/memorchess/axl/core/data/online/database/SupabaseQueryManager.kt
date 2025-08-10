package proj.memorchess.axl.core.data.online.database

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import proj.memorchess.axl.core.data.DatabaseQueryManager
import proj.memorchess.axl.core.data.PositionIdentifier
import proj.memorchess.axl.core.data.StoredNode
import proj.memorchess.axl.core.data.online.auth.AuthManager

private const val USER_NOT_CONNECTED_MESSAGE = "User must be logged in to update database"

class SupabaseQueryManager(
  private val client: SupabaseClient,
  private val authManager: AuthManager,
) : DatabaseQueryManager {

  override suspend fun getAllNodes(withDeletedOnes: Boolean): List<StoredNode> {
    val user = authManager.user
    checkNotNull(user) { USER_NOT_CONNECTED_MESSAGE }
    val result =
      client.postgrest
        .rpc("fetch_user_positions", SingleUserIdFunctionArg(user.id))
        .decodeList<PositionFetched>()
    return (if (withDeletedOnes) result else result.filter { !it.isDeleted }).map {
      it.toStoredNode()
    }
  }

  override suspend fun getPosition(positionIdentifier: PositionIdentifier): StoredNode? {
    val user = authManager.user
    checkNotNull(user) { USER_NOT_CONNECTED_MESSAGE }
    val rpc =
      client.postgrest.rpc<SinglePositionFunctionArg>(
        "fetch_single_position",
        SinglePositionFunctionArg(user.id, positionIdentifier.fenRepresentation),
      )
    val result = rpc.decodeAsOrNull<PositionFetched>()
    return if (result == null || result.isDeleted) {
      null
    } else {
      result.toStoredNode(false)
    }
  }

  override suspend fun deletePosition(position: PositionIdentifier) {
    val user = authManager.user
    checkNotNull(user) { USER_NOT_CONNECTED_MESSAGE }
    client.postgrest.rpc(
      "delete_single_position",
      SinglePositionFunctionArg(user.id, position.fenRepresentation),
    )
  }

  override suspend fun deleteMove(origin: PositionIdentifier, move: String) {
    val user = authManager.user
    checkNotNull(user) { USER_NOT_CONNECTED_MESSAGE }
    client.postgrest.rpc(
      "delete_single_move",
      MoveFromOriginFunctionArg(user.id, origin.fenRepresentation, move),
    )
  }

  override suspend fun deleteAll(hardFrom: LocalDateTime?) {
    val user = authManager.user
    checkNotNull(user) { USER_NOT_CONNECTED_MESSAGE }
    client.postgrest.rpc(
      "delete_all",
      SingleDateTimeFunctionArg(user.id, hardFrom?.toInstant(TimeZone.currentSystemDefault())),
    )
  }

  override suspend fun insertNodes(vararg positions: StoredNode) {
    val user = authManager.user
    checkNotNull(user)
    client.postgrest.rpc(
      "insert_user_positions",
      InsertPositionFunctionArg(user.id, positions.map { PositionFetched(it) }),
    )
  }

  override suspend fun getLastUpdate(): LocalDateTime? {
    val user = authManager.user
    checkNotNull(user) { USER_NOT_CONNECTED_MESSAGE }
    val rpc =
      client.postgrest.rpc<SingleUserIdFunctionArg>(
        "fetch_last_update",
        SingleUserIdFunctionArg(user.id),
      )
    return rpc.decodeAsOrNull<Instant>()?.toLocalDateTime(TimeZone.currentSystemDefault())
  }

  override fun isActive(): Boolean {
    return authManager.user != null && isSynced
  }
}
