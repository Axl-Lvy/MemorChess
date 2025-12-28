package proj.memorchess.axl.server.data

import java.util.UUID
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.dao.LongEntity
import org.jetbrains.exposed.v1.dao.LongEntityClass
import org.jetbrains.exposed.v1.dao.UUIDEntity
import org.jetbrains.exposed.v1.dao.UUIDEntityClass

class UserEntity(id: EntityID<UUID>) : UUIDEntity(id) {
  companion object : UUIDEntityClass<UserEntity>(UsersTable)

  var email by UsersTable.email
  var passwordHash by UsersTable.passwordHash
  var emailVerified by UsersTable.emailVerified
  var verificationToken by UsersTable.verificationToken
  var resetToken by UsersTable.resetToken
  var resetTokenExpiresAt by UsersTable.resetTokenExpiresAt
  var createdAt by UsersTable.createdAt
  var updatedAt by UsersTable.updatedAt
}

class PositionEntity(id: EntityID<Long>) : LongEntity(id) {
  companion object : LongEntityClass<PositionEntity>(PositionsTable)

  var fenRepresentation by PositionsTable.fenRepresentation
  var createdAt by PositionsTable.createdAt
}

class MoveEntity(id: EntityID<Long>) : LongEntity(id) {
  companion object : LongEntityClass<MoveEntity>(MovesTable)

  var origin by PositionEntity referencedOn MovesTable.origin
  var destination by PositionEntity referencedOn MovesTable.destination
  var name by MovesTable.name
  var createdAt by MovesTable.createdAt
}

class UserPositionEntity(id: EntityID<Long>) : LongEntity(id) {
  companion object : LongEntityClass<UserPositionEntity>(UserPositionsTable)

  var user by UserEntity referencedOn UserPositionsTable.userId
  var position by PositionEntity referencedOn UserPositionsTable.positionId
  var depth by UserPositionsTable.depth
  var lastTrainingDate by UserPositionsTable.lastTrainingDate
  var nextTrainingDate by UserPositionsTable.nextTrainingDate
  var isDeleted by UserPositionsTable.isDeleted
  var updatedAt by UserPositionsTable.updatedAt
  var createdAt by UserPositionsTable.createdAt
}

class UserMoveEntity(id: EntityID<Long>) : LongEntity(id) {
  companion object : LongEntityClass<UserMoveEntity>(UserMovesTable)

  var user by UserEntity referencedOn UserMovesTable.userId
  var move by MoveEntity referencedOn UserMovesTable.moveId
  var isGood by UserMovesTable.isGood
  var isDeleted by UserMovesTable.isDeleted
  var updatedAt by UserMovesTable.updatedAt
  var createdAt by UserMovesTable.createdAt
}
