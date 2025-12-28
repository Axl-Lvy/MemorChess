package proj.memorchess.axl.server.data

import java.util.UUID
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import proj.memorchess.axl.shared.data.MoveFetched
import proj.memorchess.axl.shared.data.PositionFetched

fun getAllMoves(userId: String): List<MoveFetched> {
  val moves = UserMoveEntity.find { UserMovesTable.userId eq UUID.fromString(userId) }
  val userPositionCache = mutableMapOf<Long, PositionFetched>()
  return moves.map { it.toMoveFetched(userPositionCache) }
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
