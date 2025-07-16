package proj.memorchess.axl.core.data.online.database

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.datetime.LocalDateTime
import proj.memorchess.axl.core.data.DatabaseHolder
import proj.memorchess.axl.core.data.PositionIdentifier
import proj.memorchess.axl.core.data.StoredMove
import proj.memorchess.axl.core.data.StoredNode
import proj.memorchess.axl.core.data.UnlinkedStoredNode
import proj.memorchess.axl.core.data.online.auth.SupabaseAuthManager
import proj.memorchess.axl.core.date.DateUtil
import proj.memorchess.axl.core.date.PreviousAndNextDate
import proj.memorchess.axl.core.graph.nodes.PreviousAndNextMoves

/**
 * Class that manages the remote database, and help linking its structure with the local one.
 *
 * @property authManager Authentication manager
 * @property client Supabase client
 */
class RemoteDatabaseManager(
  private val authManager: SupabaseAuthManager,
  private val client: SupabaseClient,
) {

  /**
   * Retrieve the last updates date from local and remote database.
   *
   * @return Pair(local, remote)
   */
  suspend fun getLastUpdates(): Pair<LocalDateTime?, LocalDateTime?>? {
    if (authManager.user == null) {
      return null
    }
    val lastLocalMoveUpdate = DatabaseHolder.getDatabase().getLastMoveUpdate()

    val lastRemoteMoveUpdate = fetchLastUpdated(Table.USER_MOVE)
    return Pair(lastLocalMoveUpdate, lastRemoteMoveUpdate)
  }

  /**
   * Saves a [StoredNode] to the remote database
   *
   * @param storedNode The node to upload
   */
  suspend fun saveStoredNode(storedNode: StoredNode) {
    if (authManager.user == null) {
      return
    }
    upsertPositions(
      listOf(storedNode.positionIdentifier) +
        storedNode.previousAndNextMoves.nextMoves.values.map { it.destination } +
        storedNode.previousAndNextMoves.previousMoves.values.map { it.origin }
    )
    val moves =
      storedNode.previousAndNextMoves.nextMoves.values +
        storedNode.previousAndNextMoves.previousMoves.values
    upsertMoves(moves)
    insertUnlinkedStoredNodes(
      listOf(
        UnlinkedStoredNode(
          storedNode.positionIdentifier,
          storedNode.previousAndNextTrainingDate,
          storedNode.previousAndNextMoves.depth,
          false,
        )
      )
    )
    insertMoves(moves)
  }

  suspend fun syncFromLocal() {
    uploadNodes()
    uploadMoves()
  }

  private suspend fun uploadNodes() {
    val localNodesToUpload =
      DatabaseHolder.getDatabase().getNodesUpdatedAfter(LocalDateTime(1970, 1, 1, 0, 0))
    insertUnlinkedStoredNodes(localNodesToUpload)
  }

  private suspend fun uploadMoves() {
    val localMovesToUpload =
      DatabaseHolder.getDatabase().getMovesUpdatedAfter(LocalDateTime(1970, 1, 1, 0, 0))
    insertMoves(localMovesToUpload)
  }

  suspend fun syncFromRemote() {
    updateLocalDatabase()
  }

  private suspend fun updateLocalDatabase() {
    val remoteUserMoves = client.from(Table.USER_MOVE).select().decodeList<RemoteUserMove>()
    val remoteMovesMap =
      if (remoteUserMoves.isEmpty()) mapOf()
      else
        client
          .from(Table.MOVE)
          .select {
            filter { isIn("id", remoteUserMoves.map { remoteUserMove -> remoteUserMove.moveId }) }
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
                "id",
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
          .select {
            filter {
              and {
                isIn(
                  "position_id",
                  remotePositionsMap.map { remotePosition -> remotePosition.value.id },
                )
              }
            }
          }
          .decodeList<RemoteUserPosition>()
          .associateBy { it.positionId }
    remoteUserPositionMap.values
      .filter { it.isDeleted }
      .forEach {
        DatabaseHolder.getDatabase()
          .deletePosition(remotePositionsMap[it.positionId]!!.fenRepresentation)
      }
    remoteUserMoves
      .filter { it.isDeleted }
      .forEach {
        val remoteMove = remoteMovesMap[it.moveId]!!
        DatabaseHolder.getDatabase()
          .deleteMove(remotePositionsMap[remoteMove.origin]!!.fenRepresentation, remoteMove.name)
      }
    val positionToStoredNode = mutableMapOf<PositionIdentifier, StoredNode>()
    remoteUserMoves
      .filter { !it.isDeleted }
      .forEach {
        val originPositionId = remoteMovesMap[it.moveId]!!.origin
        val origin = PositionIdentifier(remotePositionsMap[originPositionId]!!.fenRepresentation)
        val destinationPositionId = remoteMovesMap[it.moveId]!!.destination
        val destination =
          PositionIdentifier(remotePositionsMap[destinationPositionId]!!.fenRepresentation)
        val storedMove =
          StoredMove(origin, destination, remoteMovesMap[it.moveId]!!.name, it.isGood)
        positionToStoredNode
          .getOrPut(origin) {
            val originUserPosition = remoteUserPositionMap[originPositionId]!!
            StoredNode(
              origin,
              PreviousAndNextMoves(originUserPosition.depth),
              PreviousAndNextDate(
                originUserPosition.lastTrainingDate,
                originUserPosition.nextTrainingDate,
              ),
            )
          }
          .previousAndNextMoves
          .addNextMove(storedMove)
        positionToStoredNode
          .getOrPut(destination) {
            val destinationUserPosition = remoteUserPositionMap[destinationPositionId]!!
            StoredNode(
              destination,
              PreviousAndNextMoves(destinationUserPosition.depth),
              PreviousAndNextDate(
                destinationUserPosition.lastTrainingDate,
                destinationUserPosition.nextTrainingDate,
              ),
            )
          }
          .previousAndNextMoves
          .addPreviousMove(storedMove)
      }
    positionToStoredNode.values.forEach { DatabaseHolder.getDatabase().insertPosition(it) }
  }

  private suspend fun fetchLastUpdated(table: Table) =
    client
      .from(table)
      .select(Columns.list("updated_at")) {
        order(column = "updated_at", order = Order.DESCENDING)
        limit(1)
      }
      .decodeSingleOrNull<SingleUpdatedAtTime>()
      ?.updatedAt

  private suspend fun insertUnlinkedStoredNodes(nodes: List<UnlinkedStoredNode>) {
    upsertPositions(nodes.map { it.positionIdentifier })
    val positions =
      if (nodes.isEmpty()) mapOf()
      else
        client
          .from(Table.POSITION)
          .select() {
            filter {
              isIn("fen_representation", nodes.map { it.positionIdentifier.fenRepresentation })
            }
          }
          .decodeList<RemotePosition>()
          .associate { Pair(PositionIdentifier(it.fenRepresentation), it.id) }
    client.from(Table.USER_POSITION).upsert(
      nodes.map {
        RemoteUserPositionToUpload(
          authManager.user!!.id,
          positions[it.positionIdentifier]!!,
          it.depth,
          it.previousAndNextTrainingDate.nextDate,
          it.previousAndNextTrainingDate.previousDate,
          createdAt = DateUtil.now(),
          isDeleted = it.isDeleted,
          updatedAt = DateUtil.now(),
        )
      }
    ) {
      onConflict = "user_id, position_id"
      ignoreDuplicates = false
    }
  }

  private suspend fun upsertPositions(positions: Collection<PositionIdentifier>) {
    client.from(Table.POSITION).upsert(
      positions.map { RemotePositionToUpload(it.fenRepresentation) }
    ) {
      onConflict = "fen_representation"
      ignoreDuplicates = true
    }
  }

  private suspend fun insertMoves(moves: List<StoredMove>) {

    val neededPositions = moves.map { it.origin }.union(moves.map { it.destination })
    upsertPositions(neededPositions)
    upsertMoves(moves)
    val positions =
      if (neededPositions.isEmpty()) listOf()
      else
        client
          .from(Table.POSITION)
          .select {
            filter { isIn("fen_representation", neededPositions.map { it.fenRepresentation }) }
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
                    eq("origin", positionToId[it.origin]!!)
                    eq("destination", positionToId[it.destination]!!)
                    eq("name", it.move)
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
        RemoteUserMoveToUpload(
          it.value.id,
          it.key.isGood ?: false,
          DateUtil.now(),
          authManager.user!!.id,
          it.key.isDeleted,
          DateUtil.now(),
        )
      }
    ) {
      onConflict = "user_id, move_id"
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
                "fen_representation",
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
        RemoteMoveToUpload(positionToId[it.origin]!!, positionToId[it.destination]!!, it.move)
      }
    ) {
      onConflict = "origin, destination, name"
      ignoreDuplicates = true
    }
  }
}
