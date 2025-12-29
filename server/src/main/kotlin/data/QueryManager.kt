package proj.memorchess.axl.server.data

import java.util.UUID
import kotlin.time.Instant
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.deleteWhere
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
    UserMoveEntity.find {
      UserMovesTable.userId eq UUID.fromString(userId) and (UserMovesTable.isDeleted eq false)
    }
  }
  val userPositionCache = mutableMapOf<Long, PositionFetched>()
  return transaction { moves.map { it.toMoveFetched(userPositionCache) } }
}

fun addMoves(userId: String, moves: List<MoveFetched>) {
  transaction {
    val userEntity =
      UserEntity.find { UsersTable.id eq UUID.fromString(userId) }.firstOrNull()
        ?: return@transaction
    for (move in moves) {
      val originPositionEntity = updatePosition(move.origin, userEntity)
      val destinationPositionEntity = updatePosition(move.destination, userEntity)
      val moveEntity =
        MoveEntity.find {
            (MovesTable.origin eq originPositionEntity.id.value) and
              (MovesTable.destination eq destinationPositionEntity.id.value) and
              (MovesTable.name eq move.move)
          }
          .firstOrNull()
          ?: MoveEntity.new {
            origin = originPositionEntity
            destination = destinationPositionEntity
            name = move.move
          }
      UserMoveEntity.new {
        this.user = userEntity
        this.move = moveEntity
        this.isGood = move.isGood
        this.isDeleted = move.isDeleted
      }
    }
  }
}

private fun updatePosition(position: PositionFetched, userEntity: UserEntity): PositionEntity {
  val originPositionEntity =
    PositionEntity.find { PositionsTable.fenRepresentation eq position.positionIdentifier }
      .firstOrNull() ?: PositionEntity.new { fenRepresentation = position.positionIdentifier }
  UserPositionEntity.find {
      (UserPositionsTable.userId eq userEntity.id.value) and
        (UserPositionsTable.positionId eq originPositionEntity.id.value)
    }
    .firstOrNull()
    ?.let {
      it.depth = position.depth
      it.lastTrainingDate = position.lastTrainingDate
      it.nextTrainingDate = position.nextTrainingDate
      it.isDeleted = position.isDeleted
      it.updatedAt = position.updatedAt
    }
    ?: UserPositionEntity.new {
      this.user = userEntity
      this.position = originPositionEntity
      this.depth = position.depth
      this.lastTrainingDate = position.lastTrainingDate
      this.nextTrainingDate = position.nextTrainingDate
      this.isDeleted = position.isDeleted
      this.updatedAt = position.updatedAt
    }
  return originPositionEntity
}

fun getNode(userId: String, fen: String): NodeFetched? {
  return transaction {
    val positionQuery =
      UserPositionsTable.innerJoin(PositionsTable)
        .select(UserPositionsTable.columns)
        .where {
          (UserPositionsTable.userId eq UUID.fromString(userId)) and
            (PositionsTable.fenRepresentation eq fen) and
            (UserPositionsTable.isDeleted eq false)
        }
        .limit(1)
    val userPosition =
      positionQuery.firstOrNull()?.let { UserPositionEntity.wrapRow(it) } ?: return@transaction null
    val query =
      UserMovesTable.innerJoin(MovesTable).select(UserMovesTable.columns).where {
        (UserMovesTable.userId eq UUID.fromString(userId)) and
          ((MovesTable.origin eq userPosition.position.id.value) or
            (MovesTable.destination eq userPosition.position.id.value)) and
          (UserMovesTable.isDeleted eq false)
      }

    val userMoves = UserMoveEntity.wrapRows(query)
    val userPositionCache =
      mutableMapOf(Pair(userPosition.id.value, userPosition.toPositionFetched()))
    NodeFetched(
      position = userPosition.toPositionFetched(),
      moves = transaction { userMoves.map { it.toMoveFetched(userPositionCache) } },
    )
  }
}

fun deletePosition(userId: String, positionIdentifier: String) {
  transaction {
    val positionEntity =
      PositionEntity.find { PositionsTable.fenRepresentation eq positionIdentifier }.firstOrNull()
        ?: return@transaction

    val userPosition =
      UserPositionEntity.find {
          (UserPositionsTable.userId eq UUID.fromString(userId)) and
            (UserPositionsTable.positionId eq positionEntity.id.value)
        }
        .firstOrNull() ?: return@transaction

    userPosition.isDeleted = true
  }
}

fun deleteMove(userId: String, origin: String, move: String) {
  transaction {
    val originPosition =
      PositionEntity.find { PositionsTable.fenRepresentation eq origin }.firstOrNull()
        ?: return@transaction

    val moveEntity =
      MoveEntity.find {
          (MovesTable.origin eq originPosition.id.value) and (MovesTable.name eq move)
        }
        .firstOrNull() ?: return@transaction

    val userMove =
      UserMoveEntity.find {
          (UserMovesTable.userId eq UUID.fromString(userId)) and
            (UserMovesTable.moveId eq moveEntity.id.value)
        }
        .firstOrNull() ?: return@transaction

    userMove.isDeleted = true
  }
}

fun deleteAllUserData(userId: String, hardFrom: Instant?) {
  transaction {
    if (hardFrom != null) {
      UserPositionsTable.deleteWhere {
        UserPositionsTable.userId eq
          UUID.fromString(userId) and
          (UserPositionsTable.updatedAt greater hardFrom)
      }
      UserMovesTable.deleteWhere {
        UserMovesTable.userId eq
          UUID.fromString(userId) and
          (UserMovesTable.updatedAt greater hardFrom)
      }
    }
    UserPositionEntity.find { UserPositionsTable.userId eq UUID.fromString(userId) }
      .forEach { it.isDeleted = true }
    UserMoveEntity.find { UserMovesTable.userId eq UUID.fromString(userId) }
      .forEach { it.isDeleted = true }
  }
}

fun getLastUpdate(userId: String): Instant? {
  return transaction {
    val lastMoveUpdate =
      UserMovesTable.select(UserMovesTable.updatedAt)
        .where { UserMovesTable.userId eq UUID.fromString(userId) }
        .orderBy(UserMovesTable.updatedAt, order = SortOrder.DESC)
        .limit(1)
        .firstOrNull()
        ?.get(UserMovesTable.updatedAt)

    val lastPositionUpdate =
      UserPositionsTable.select(UserPositionsTable.updatedAt)
        .where { UserPositionsTable.userId eq UUID.fromString(userId) }
        .orderBy(UserPositionsTable.updatedAt, order = SortOrder.DESC)
        .limit(1)
        .firstOrNull()
        ?.get(UserPositionsTable.updatedAt)

    when {
      lastMoveUpdate != null && lastPositionUpdate != null ->
        lastMoveUpdate.coerceAtLeast(lastPositionUpdate)
      else -> lastMoveUpdate ?: lastPositionUpdate
    }
  }
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
