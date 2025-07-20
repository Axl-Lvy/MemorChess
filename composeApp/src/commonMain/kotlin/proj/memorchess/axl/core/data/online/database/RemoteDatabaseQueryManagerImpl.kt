package proj.memorchess.axl.core.data.online.database

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.PostgrestFilterDSL
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.query.PostgrestQueryBuilder
import io.github.jan.supabase.postgrest.query.PostgrestUpdate
import io.github.jan.supabase.postgrest.query.filter.PostgrestFilterBuilder
import io.github.jan.supabase.postgrest.result.PostgrestResult
import kotlinx.coroutines.selects.select
import kotlinx.datetime.LocalDateTime
import proj.memorchess.axl.core.data.PositionIdentifier
import proj.memorchess.axl.core.data.StoredMove
import proj.memorchess.axl.core.data.StoredNode
import proj.memorchess.axl.core.data.UnlinkedStoredNode
import proj.memorchess.axl.core.data.online.auth.AuthManager
import proj.memorchess.axl.core.date.DateUtil
import proj.memorchess.axl.core.date.DateUtil.truncateToSeconds
import proj.memorchess.axl.core.date.PreviousAndNextDate
import proj.memorchess.axl.core.graph.nodes.PreviousAndNextMoves

class RemoteDatabaseQueryManagerImpl(
  private val client: SupabaseClient,
  private val authManager: AuthManager,
) : RemoteDatabaseQueryManager {
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

  override suspend fun getAllNodes(): List<StoredNode> {
    val remoteUserMoves =
      client.from(Table.USER_MOVE).selectWithUserFilter().decodeList<RemoteUserMove>()
    val remoteMovesMap =
      if (remoteUserMoves.isEmpty()) mapOf()
      else
        client
          .from(Table.MOVE)
          .select {
            filter {
              isIn(ID_FIELD, remoteUserMoves.map { remoteUserMove -> remoteUserMove.moveId })
            }
          }
          .decodeList<RemoteMove>()
          .associateBy { it.id }
    val remotePositionsMap =
      if (remoteMovesMap.isEmpty()) mapOf()
      else
        client
          .from(Table.POSITION)
          .select {
            filter {
              isIn(
                ID_FIELD,
                remoteMovesMap.values
                  .map { remoteMove -> remoteMove.origin }
                  .union(remoteMovesMap.values.map { remoteMove -> remoteMove.destination })
                  .toList(),
              )
            }
          }
          .decodeList<RemotePosition>()
          .associateBy { it.id }
    val remoteUserPositionMap =
      if (remotePositionsMap.isEmpty()) mapOf()
      else
        client
          .from(Table.USER_POSITION)
          .selectWithUserFilter {
            isIn(
              POSITION_ID_FIELD,
              remotePositionsMap.map { remotePosition -> remotePosition.value.id },
            )
          }
          .decodeList<RemoteUserPosition>()
          .associateBy { it.positionId }
    val positionToStoredNode = mutableMapOf<PositionIdentifier, StoredNode>()
    remoteUserMoves
      .filter { !it.isDeleted }
      .forEach {
        val remoteMove = remoteMovesMap[it.moveId]
        checkNotNull(remoteMove)
        val originPositionId = remoteMove.origin
        val remoteOrigin = remotePositionsMap[originPositionId]
        checkNotNull(remoteOrigin)
        val origin = PositionIdentifier(remoteOrigin.fenRepresentation)
        val destinationPositionId = remoteMove.destination
        val remoteDestination = remotePositionsMap[destinationPositionId]
        checkNotNull(remoteDestination)
        val destination = PositionIdentifier(remoteDestination.fenRepresentation)
        val storedMove =
          StoredMove(origin, destination, remoteMove.name, it.isGood, it.isDeleted, it.updatedAt)
        val remoteUserOrigin = remoteUserPositionMap[originPositionId]
        if (remoteUserOrigin != null) {
          positionToStoredNode
            .getOrPut(origin) {
              val originUserPosition = remoteUserOrigin
              StoredNode(
                origin,
                PreviousAndNextMoves(originUserPosition.depth),
                PreviousAndNextDate(
                  originUserPosition.lastTrainingDate,
                  originUserPosition.nextTrainingDate,
                ),
                originUserPosition.updatedAt,
              )
            }
            .previousAndNextMoves
            .addNextMove(storedMove)
        }
        val remoteUserDestination = remoteUserPositionMap[destinationPositionId]
        if (remoteUserDestination != null) {
          positionToStoredNode
            .getOrPut(destination) {
              val destinationUserPosition = remoteUserDestination
              StoredNode(
                destination,
                PreviousAndNextMoves(destinationUserPosition.depth),
                PreviousAndNextDate(
                  destinationUserPosition.lastTrainingDate,
                  destinationUserPosition.nextTrainingDate,
                ),
                destinationUserPosition.updatedAt,
              )
            }
            .previousAndNextMoves
            .addPreviousMove(storedMove)
        }
      }
    return positionToStoredNode.values.toList()
  }

  override suspend fun getAllPositions(): List<UnlinkedStoredNode> {
    val userPositions =
      client.from(Table.USER_POSITION).selectWithUserFilter().decodeList<RemoteUserPosition>()
    val idToPosition =
      client
        .from(Table.POSITION)
        .select { filter { isIn(ID_FIELD, userPositions.map { it.positionId }) } }
        .decodeList<RemotePosition>()
        .associateBy { it.id }
    return userPositions.map {
      val position = idToPosition[it.positionId]
      checkNotNull(position)
      UnlinkedStoredNode(
        PositionIdentifier(position.fenRepresentation),
        PreviousAndNextDate(it.lastTrainingDate, it.nextTrainingDate),
        it.depth,
        it.isDeleted,
        it.updatedAt,
      )
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

  override suspend fun insertMove(move: StoredMove) {
    insertMoves(listOf(move))
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

  override suspend fun insertPosition(position: StoredNode) {
    insertUnlinkedStoredNodes(
      listOf(
        UnlinkedStoredNode(
          position.positionIdentifier,
          position.previousAndNextTrainingDate,
          position.previousAndNextMoves.depth,
          false,
          position.updatedAt,
        )
      )
    )
    insertMoves(
      position.previousAndNextMoves.nextMoves.values +
        position.previousAndNextMoves.previousMoves.values
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

  override suspend fun insertUnlinkedStoredNodes(nodes: List<UnlinkedStoredNode>) {
    upsertPositions(nodes.map { it.positionIdentifier })
    val positions =
      if (nodes.isEmpty()) mapOf()
      else
        client
          .from(Table.POSITION)
          .select() {
            filter {
              isIn(FEN_REPRESENTATION_FIELD, nodes.map { it.positionIdentifier.fenRepresentation })
            }
          }
          .decodeList<RemotePosition>()
          .associate { Pair(PositionIdentifier(it.fenRepresentation), it.id) }
    client.from(Table.USER_POSITION).upsert(
      nodes.map {
        val user = authManager.user
        checkNotNull(user)
        val positionId = positions[it.positionIdentifier]
        checkNotNull(positionId)
        RemoteUserPositionToUpload(
          user.id,
          positionId,
          it.depth,
          it.previousAndNextTrainingDate.nextDate,
          it.previousAndNextTrainingDate.previousDate,
          createdAt = DateUtil.now(),
          isDeleted = it.isDeleted,
          updatedAt = it.updatedAt,
        )
      }
    ) {
      onConflict = "$USER_ID_FIELD, $POSITION_ID_FIELD"
      ignoreDuplicates = false
    }
  }

  private suspend fun upsertPositions(positions: Collection<PositionIdentifier>) {
    client.from(Table.POSITION).upsert(
      positions.map { RemotePositionToUpload(it.fenRepresentation) }
    ) {
      onConflict = FEN_REPRESENTATION_FIELD
      ignoreDuplicates = true
    }
  }

  override suspend fun insertMoves(moves: List<StoredMove>) {

    val neededPositions = moves.map { it.origin }.union(moves.map { it.destination })
    upsertPositions(neededPositions)
    upsertMoves(moves)
    val positions =
      if (neededPositions.isEmpty()) listOf()
      else
        client
          .from(Table.POSITION)
          .select {
            filter { isIn(FEN_REPRESENTATION_FIELD, neededPositions.map { it.fenRepresentation }) }
          }
          .decodeList<RemotePosition>()
    val positionToId = positions.associate { Pair(PositionIdentifier(it.fenRepresentation), it.id) }
    val idToPosition = positions.associate { Pair(it.id, PositionIdentifier(it.fenRepresentation)) }
    val remoteMoveList =
      if (moves.isEmpty()) listOf()
      else
        client
          .from(Table.MOVE)
          .select {
            filter {
              or {
                moves.forEach {
                  and {
                    val originId = positionToId[it.origin]
                    checkNotNull(originId)
                    eq(ORIGIN_FIELD, originId)
                    val destinationId = positionToId[it.destination]
                    checkNotNull(destinationId)
                    eq(DESTINATION_FIELD, destinationId)
                    eq(NAME_FIELD, it.move)
                  }
                }
              }
            }
          }
          .decodeList<RemoteMove>()
    val moveToRemoteMove =
      remoteMoveList.associateBy {
        val origin = idToPosition[it.origin]
        checkNotNull(origin)
        val destination = idToPosition[it.destination]
        checkNotNull(destination)
        val firstOrNull =
          moves.firstOrNull { move ->
            move.move == it.name && move.origin == origin && move.destination == destination
          }
        checkNotNull(firstOrNull)
        firstOrNull
      }
    client.from(Table.USER_MOVE).upsert(
      moveToRemoteMove.map {
        val user = authManager.user
        checkNotNull(user)
        RemoteUserMoveToUpload(
          it.value.id,
          it.key.isGood ?: false,
          DateUtil.now(),
          user.id,
          it.key.isDeleted,
          it.key.updatedAt,
        )
      }
    ) {
      onConflict = "$USER_ID_FIELD, $MOVE_ID_FIELD"
      ignoreDuplicates = false
    }
  }

  private suspend fun upsertMoves(moves: List<StoredMove>) {
    val positionToId =
      if (moves.isEmpty()) mapOf()
      else
        client
          .from(Table.POSITION)
          .select {
            filter {
              isIn(
                FEN_REPRESENTATION_FIELD,
                moves
                  .map { it.origin.fenRepresentation }
                  .union(moves.map { it.destination.fenRepresentation })
                  .toList(),
              )
            }
          }
          .decodeList<RemotePosition>()
          .associate { Pair(PositionIdentifier(it.fenRepresentation), it.id) }
    client.from(Table.MOVE).upsert(
      moves.map {
        val originId = positionToId[it.origin]
        checkNotNull(originId)
        val destination = positionToId[it.destination]
        checkNotNull(destination)
        RemoteMoveToUpload(originId, destination, it.move)
      }
    ) {
      onConflict = "$ORIGIN_FIELD, $DESTINATION_FIELD, $NAME_FIELD"
      ignoreDuplicates = true
    }
  }

  override fun isActive(): Boolean {
    return authManager.user != null && isSynced
  }
}
