package proj.memorchess.axl.core.data.online.database

import com.diamondedge.logging.logging
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
import proj.memorchess.axl.core.data.StoredMove
import proj.memorchess.axl.core.data.StoredNode
import proj.memorchess.axl.core.data.online.auth.AuthManager
import proj.memorchess.axl.core.date.DateUtil.truncateToSeconds
import proj.memorchess.axl.core.date.PreviousAndNextDate
import proj.memorchess.axl.core.graph.nodes.PreviousAndNextMoves

class RemoteDatabaseQueryManager(
  private val client: SupabaseClient,
  private val authManager: AuthManager,
) : DatabaseQueryManager {
  // Helper extension functions to automatically apply user ID filter
  private suspend fun PostgrestQueryBuilder.selectWithUserFilter(
    block: @PostgrestFilterDSL (PostgrestFilterBuilder.() -> Unit) = {}
  ): PostgrestResult {
    val user = authManager.user
    checkNotNull(user) { "User must be logged in to select" }
    return select {
      filter {
        and {
          eq(USER_ID_FIELD, user.id)
          block()
        }
      }
    }
  }

  private suspend fun PostgrestQueryBuilder.updateWithUserFilter(
    update: PostgrestUpdate.() -> Unit,
    block: @PostgrestFilterDSL (PostgrestFilterBuilder.() -> Unit) = {},
  ): PostgrestResult {
    val user = authManager.user
    checkNotNull(user) { "User must be logged in to update database" }
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
    checkNotNull(user) { "User must be logged in to update database" }
    val result =
      client.postgrest
        .rpc("fetch_user_positions", SingleUserIdInput(user.id))
        .decodeList<PositionToUpload>()

    LOGGER.error { "fetched ${result.size} nodes: $result" }
    return (if (withDeletedOnes) result else result.filter { !it.isDeleted }).map {
      it.toStoredNode()
    }
  }

  override suspend fun getPosition(positionIdentifier: PositionIdentifier): StoredNode? {
    val position =
      client
        .from(Table.POSITION)
        .select { filter { eq(FEN_REPRESENTATION_FIELD, positionIdentifier.fenRepresentation) } }
        .decodeAsOrNull<RemotePosition>()
    if (position == null) {
      return null
    }
    val userPosition =
      client
        .from(Table.USER_POSITION)
        .selectWithUserFilter {
          and {
            eq(POSITION_ID_FIELD, position.id)
            eq(IS_DELETED_FIELD, false)
          }
        }
        .decodeAsOrNull<RemoteUserPosition>()
    if (userPosition == null) {
      return null
    }
    check(position.fenRepresentation == positionIdentifier.fenRepresentation)
    val result =
      StoredNode(
        PositionIdentifier(position.fenRepresentation),
        PreviousAndNextMoves(userPosition.depth),
        PreviousAndNextDate(userPosition.lastTrainingDate, userPosition.nextTrainingDate),
        userPosition.updatedAt,
      )
    val idToMove =
      client
        .from(Table.MOVE)
        .select {
          filter {
            or {
              eq(ORIGIN_FIELD, position.id)
              eq(DESTINATION_FIELD, position.id)
            }
          }
        }
        .decodeList<RemoteMove>()
        .associateBy { it.id }
    val idToPosition =
      client
        .from(Table.POSITION)
        .select() {
          filter {
            isIn(
              ID_FIELD,
              idToMove.values
                .map { it.origin }
                .union(idToMove.values.map { it.destination })
                .toList(),
            )
          }
        }
        .decodeList<RemotePosition>()
        .associateBy { it.id }
    val remoteUserMoves =
      client
        .from(Table.USER_MOVE)
        .selectWithUserFilter {
          and {
            eq(USER_ID_FIELD, authManager.user!!.id)
            isIn(MOVE_ID_FIELD, idToMove.keys.toList())
          }
        }
        .decodeList<RemoteUserMove>()
    remoteUserMoves
      .map {
        val move = idToMove[it.moveId]
        checkNotNull(move)
        val origin = idToPosition[move.origin]
        checkNotNull(origin)
        val destination = idToPosition[move.destination]
        checkNotNull(destination)
        StoredMove(
          PositionIdentifier(origin.fenRepresentation),
          PositionIdentifier(destination.fenRepresentation),
          move.name,
          it.isGood,
          it.isDeleted,
          it.updatedAt,
        )
      }
      .forEach {
        if (it.origin == positionIdentifier) {
          result.previousAndNextMoves.addNextMove(it)
        } else {
          check(it.destination == positionIdentifier)
          result.previousAndNextMoves.addPreviousMove(it)
        }
      }
    return result
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

  override suspend fun getAllMoves(withDeletedOnes: Boolean): List<StoredMove> {
    val userMoves =
      client
        .from(Table.USER_MOVE)
        .selectWithUserFilter() {
          if (!withDeletedOnes) {
            eq(IS_DELETED_FIELD, false)
          }
        }
        .decodeList<RemoteUserMove>()
    if (userMoves.isEmpty()) {
      return listOf()
    }
    val idToMove =
      client
        .from(Table.MOVE)
        .select { filter { isIn(ID_FIELD, userMoves.map { it.moveId }) } }
        .decodeList<RemoteMove>()
        .associateBy { it.id }
    val idToPosition =
      client
        .from(Table.POSITION)
        .select {
          filter {
            isIn(
              ID_FIELD,
              idToMove.values
                .map { it.origin }
                .union(idToMove.values.map { it.destination })
                .toList(),
            )
          }
        }
        .decodeList<RemotePosition>()
        .associateBy { it.id }
    return userMoves.map {
      val move = idToMove[it.moveId]
      checkNotNull(move)
      val origin = idToPosition[move.origin]
      checkNotNull(origin)
      val destination = idToPosition[move.destination]
      checkNotNull(destination)
      StoredMove(
        PositionIdentifier(origin.fenRepresentation),
        PositionIdentifier(destination.fenRepresentation),
        move.name,
        it.isGood,
        it.isDeleted,
        it.updatedAt,
      )
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

  private suspend fun insertMoves(moves: List<StoredMove>) {
    LOGGER.error { "inserting ${moves.size} moves" }
    val user = authManager.user
    checkNotNull(user)
    client.postgrest.rpc(
      "insert_user_moves",
      InsertMoveFunctionArg(user.id, moves.map { MoveToUpload(it) }),
    )
  }

  override fun isActive(): Boolean {
    return authManager.user != null && isSynced
  }
}

private val LOGGER = logging()
