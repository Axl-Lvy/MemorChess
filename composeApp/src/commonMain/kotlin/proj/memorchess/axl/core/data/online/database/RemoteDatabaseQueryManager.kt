package proj.memorchess.axl.core.data.online.database

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.PostgrestFilterDSL
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.query.PostgrestQueryBuilder
import io.github.jan.supabase.postgrest.query.PostgrestUpdate
import io.github.jan.supabase.postgrest.query.filter.PostgrestFilterBuilder
import io.github.jan.supabase.postgrest.result.PostgrestResult
import io.github.jan.supabase.postgrest.rpc
import kotlinx.datetime.LocalDateTime
import proj.memorchess.axl.core.data.DatabaseQueryManager
import proj.memorchess.axl.core.data.PositionIdentifier
import proj.memorchess.axl.core.data.StoredNode
import proj.memorchess.axl.core.data.online.auth.AuthManager
import proj.memorchess.axl.core.date.DateUtil.truncateToSeconds

private const val USER_NOT_CONNECTED_MESSAGE = "User must be logged in to update database"

class RemoteDatabaseQueryManager(
  private val client: SupabaseClient,
  private val authManager: AuthManager,
) : DatabaseQueryManager {

  private suspend fun PostgrestQueryBuilder.updateWithUserFilter(
    update: PostgrestUpdate.() -> Unit,
    block: @PostgrestFilterDSL (PostgrestFilterBuilder.() -> Unit) = {},
  ): PostgrestResult {
    val user = authManager.user
    checkNotNull(user) { USER_NOT_CONNECTED_MESSAGE }
    return update(update) {
      filter {
        and {
          eq(USER_ID_FIELD, user.id)
          block()
        }
      }
    }
  }

  override suspend fun getAllNodes(withDeletedOnes: Boolean): List<StoredNode> {
    val user = authManager.user
    checkNotNull(user) { USER_NOT_CONNECTED_MESSAGE }
    val result =
      client.postgrest
        .rpc("fetch_user_positions", SingleUserIdInput(user.id))
        .decodeList<PositionToUpload>()
    return (if (withDeletedOnes) result else result.filter { !it.isDeleted }).map {
      it.toStoredNode()
    }
  }

  override suspend fun getPosition(positionIdentifier: PositionIdentifier): StoredNode? {
    val user = authManager.user
    checkNotNull(user) { USER_NOT_CONNECTED_MESSAGE }
    val result =
      client.postgrest
        .rpc(
          "fetch_single_position",
          FetchSinglePositionFunctionArg(user.id, positionIdentifier.fenRepresentation),
        )
        .decodeSingleOrNull<PositionToUpload>()
    return if (result == null || result.isDeleted) {
      null
    } else {
      result.toStoredNode(false)
    }
  }

  override suspend fun deletePosition(fen: String) {
    val position =
      client
        .from(Table.POSITION)
        .select { filter { eq(FEN_REPRESENTATION_FIELD, fen) } }
        .decodeAsOrNull<RemotePosition>()
    if (position == null) {
      return
    }
    client.from(Table.USER_POSITION).updateWithUserFilter({ set(IS_DELETED_FIELD, true) }) {
      eq(POSITION_ID_FIELD, position.id)
    }
  }

  override suspend fun deleteMove(origin: String, move: String) {
    val position =
      client
        .from(Table.POSITION)
        .select { filter { eq(FEN_REPRESENTATION_FIELD, origin) } }
        .decodeAsOrNull<RemotePosition>()
    if (position == null) {
      return
    }
    val moves =
      client
        .from(Table.MOVE)
        .select {
          filter {
            and { eq(NAME_FIELD, move) }
            eq(ORIGIN_FIELD, position.id)
          }
        }
        .decodeList<RemoteMove>()
    if (moves.isEmpty()) {
      return
    }
    client.from(Table.USER_MOVE).updateWithUserFilter({ set(IS_DELETED_FIELD, true) }) {
      isIn(MOVE_ID_FIELD, moves.map { it.id })
    }
  }

  override suspend fun deleteAll(hardFrom: LocalDateTime?) {
    client.from(Table.USER_POSITION).updateWithUserFilter({ set(IS_DELETED_FIELD, true) })
    client.from(Table.USER_MOVE).updateWithUserFilter({ set(IS_DELETED_FIELD, true) })
    hardFrom?.let {
      client.from(Table.USER_POSITION).delete { filter { gt(UPDATED_AT_FIELD, it) } }
      client.from(Table.USER_MOVE).delete { filter { gt(UPDATED_AT_FIELD, it) } }
    }
  }

  override suspend fun insertNodes(vararg positions: StoredNode) {
    val user = authManager.user
    checkNotNull(user)
    client.postgrest.rpc(
      "insert_user_positions",
      InsertPositionFunctionArg(user.id, positions.map { PositionToUpload(it) }),
    )
  }

  override suspend fun getLastUpdate(): LocalDateTime? {
    val moveUpdate =
      client
        .from(Table.USER_MOVE)
        .select(Columns.list(UPDATED_AT_FIELD)) {
          filter {
            val user = authManager.user
            checkNotNull(user)
            eq(USER_ID_FIELD, user.id)
          }
          order(column = UPDATED_AT_FIELD, order = Order.DESCENDING)
          limit(1)
        }
        .decodeSingleOrNull<SingleUpdatedAtTime>()
        ?.updatedAt

    val positionUpdate =
      client
        .from(Table.USER_POSITION)
        .select(Columns.list(UPDATED_AT_FIELD)) {
          filter {
            val user = authManager.user
            checkNotNull(user)
            eq(USER_ID_FIELD, user.id)
          }
          order(column = UPDATED_AT_FIELD, order = Order.DESCENDING)
          limit(1)
        }
        .decodeSingleOrNull<SingleUpdatedAtTime>()
        ?.updatedAt
    return (if (moveUpdate != null && positionUpdate != null) {
        moveUpdate.coerceAtLeast(positionUpdate)
      } else {
        moveUpdate ?: positionUpdate
      })
      ?.truncateToSeconds()
  }

  override fun isActive(): Boolean {
    return authManager.user != null && isSynced
  }
}
