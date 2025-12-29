package proj.memorchess.axl.server.data

import java.util.UUID
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import proj.memorchess.axl.shared.data.MoveFetched
import proj.memorchess.axl.shared.data.NodeFetched
import proj.memorchess.axl.shared.data.PositionFetched

fun getUser(email: String): UserEntity? {
  return transaction { UserEntity.find { UsersTable.email eq email }.firstOrNull() }
}

fun hasUserPermission(userId: String, permission: String): Boolean {
  return transaction {
    !UserPermissionEntity.find {
        (UserPermissionsTable.userId eq UUID.fromString(userId)) and
          (UserPermissionsTable.permission eq permission)
      }
      .empty()
  }
}

fun getAllMoves(userId: String): List<MoveFetched> {
  val moves = transaction {
    UserMoveEntity.find { UserMovesTable.userId eq UUID.fromString(userId) }
  }
  val userPositionCache = mutableMapOf<Long, PositionFetched>()
  return transaction { moves.map { it.toMoveFetched(userPositionCache) } }
}

fun getNode(userId: String, fen: String): NodeFetched? {
  val queryPosition =
    transaction {
      UserPositionsTable.innerJoin(PositionsTable)
        .select(UserPositionsTable.columns)
        .where {
          (UserPositionsTable.userId eq UUID.fromString(userId)) and
            (PositionsTable.fenRepresentation eq fen)
        }
        .limit(1)
        .firstOrNull()
    } ?: return null
  val userPosition = UserPositionEntity.wrapRow(queryPosition)
  val queryMoves = transaction {
    UserMovesTable.innerJoin(MovesTable).select(UserMovesTable.columns).where {
      (UserMovesTable.userId eq UUID.fromString(userId)) and
        ((MovesTable.origin eq userPosition.position.id.value) or
          (MovesTable.destination eq userPosition.position.id.value))
    }
  }
  val userMoves = UserMoveEntity.wrapRows(queryMoves)
  val userPositionCache =
    mutableMapOf(Pair(userPosition.id.value, userPosition.toPositionFetched()))
  return NodeFetched(
    position = userPosition.toPositionFetched(),
    moves = transaction { userMoves.map { it.toMoveFetched(userPositionCache) } },
  )
}

private fun UserPositionEntity.toPositionFetched(): PositionFetched {
  return PositionFetched(
    positionIdentifier = position.fenRepresentation,
    depth = depth,
    lastTrainingDate = lastTrainingDate,
    nextTrainingDate = nextTrainingDate,
    updatedAt = updatedAt,
    isDeleted = isDeleted,
  )
}

private fun UserMoveEntity.toMoveFetched(
  userPositionCache: MutableMap<Long, PositionFetched>
): MoveFetched {
  val originPosition =
    userPositionCache.getOrPut(move.origin.id.value) {
      UserPositionEntity.find {
          (UserPositionsTable.userId eq user.id.value) and
            (UserPositionsTable.positionId eq move.origin.id.value)
        }
        .firstOrNull()
        ?.toPositionFetched() ?: throw IllegalStateException("UserPosition not found")
    }
  val destinationPosition =
    userPositionCache.getOrPut(move.destination.id.value) {
      UserPositionEntity.find {
          (UserPositionsTable.userId eq user.id.value) and
            (UserPositionsTable.positionId eq move.destination.id.value)
        }
        .firstOrNull()
        ?.toPositionFetched() ?: throw IllegalStateException("UserPosition not found")
    }
  return MoveFetched(
    origin = originPosition,
    destination = destinationPosition,
    move = move.name,
    isGood = isGood,
    isDeleted = isDeleted,
    updatedAt = updatedAt,
  )
}
