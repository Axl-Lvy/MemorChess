package proj.memorchess.axl.server.data

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import org.jetbrains.exposed.v1.core.dao.id.UUIDTable
import org.jetbrains.exposed.v1.datetime.CurrentTimestamp
import org.jetbrains.exposed.v1.datetime.date
import org.jetbrains.exposed.v1.datetime.timestamp

/** Exposed table definition for users. */
object UsersTable : UUIDTable("users") {
  val email = varchar("email", 255).uniqueIndex()
  val passwordHash = varchar("password_hash", 255)
  val emailVerified = bool("email_verified").default(false)
  val verificationToken = varchar("verification_token", 255).nullable()
  val resetToken = varchar("reset_token", 255).nullable()
  val resetTokenExpiresAt = timestamp("reset_token_expires_at").nullable()
  val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
  val updatedAt = timestamp("updated_at").defaultExpression(CurrentTimestamp)
}

/** Exposed table definition for positions. */
object PositionsTable : LongIdTable("positions") {
  val fenRepresentation = text("fen_representation").uniqueIndex()
  val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
}

/** Exposed table definition for moves. */
object MovesTable : LongIdTable("moves") {
  val origin = reference("origin", PositionsTable.id, onDelete = ReferenceOption.CASCADE)
  val destination = reference("destination", PositionsTable.id, onDelete = ReferenceOption.CASCADE)
  val name = text("name")
  val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)

  init {
    uniqueIndex(origin, name)
    uniqueIndex(origin, destination)
  }
}

/** Exposed table definition for user positions. */
object UserPositionsTable : LongIdTable("user_positions") {
  val userId = uuid("user_id").index()
  val positionId =
    reference("position_id", PositionsTable.id, onDelete = ReferenceOption.CASCADE).index()
  val depth = integer("depth").default(0)
  val lastTrainingDate = date("last_training_date")
  val nextTrainingDate = date("next_training_date").index()
  val isDeleted = bool("is_deleted").default(false)
  val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
  val updatedAt = timestamp("updated_at").defaultExpression(CurrentTimestamp)

  init {
    uniqueIndex(userId, positionId)
  }
}

/** Exposed table definition for user moves. */
object UserMovesTable : LongIdTable("user_moves") {
  val userId = uuid("user_id").index()
  val moveId = reference("move_id", MovesTable.id, onDelete = ReferenceOption.CASCADE).index()
  val isGood = bool("is_good").default(true)
  val isDeleted = bool("is_deleted").default(false)
  val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
  val updatedAt = timestamp("updated_at").defaultExpression(CurrentTimestamp)

  init {
    uniqueIndex(userId, moveId)
  }
}

/** Exposed table definition for books. */
object BooksTable : LongIdTable("books") {
  val name = text("name").index()
  val downloads = long("downloads").default(0).index()
  val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp).index()
}

/** Exposed table definition for move_cross_book (many-to-many relationship). */
object MoveCrossBookTable : LongIdTable("move_cross_book") {
  val moveId = reference("move_id", MovesTable.id, onDelete = ReferenceOption.CASCADE).index()
  val bookId = reference("book_id", BooksTable.id, onDelete = ReferenceOption.CASCADE).index()
  val isGood = bool("is_good").default(true)
  val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)

  init {
    uniqueIndex(moveId, bookId)
  }
}

/** Exposed table definition for downloaded books. */
object DownloadedBooksTable : LongIdTable("downloaded_books") {
  val userId = uuid("user_id").index()
  val bookId = reference("book_id", BooksTable.id, onDelete = ReferenceOption.CASCADE).index()
  val downloadedAt = timestamp("downloaded_at").defaultExpression(CurrentTimestamp)

  init {
    uniqueIndex(userId, bookId)
  }
}

/** Exposed table definition for user permissions. */
object UserPermissionsTable : LongIdTable("user_permissions") {
  val userId = uuid("user_id").index()
  val permission = varchar("permission", 50)
  val grantedAt = timestamp("granted_at").defaultExpression(CurrentTimestamp)

  init {
    uniqueIndex(userId, permission)
  }
}

/**
 * Returns all table definitions in this file using reflection.
 *
 * This automatically discovers all objects that extend [Table], eliminating the need to manually
 * maintain a list of tables.
 */
val ALL_TABLES: Array<Table> =
  arrayOf(
    UsersTable,
    PositionsTable,
    MovesTable,
    UserPositionsTable,
    UserMovesTable,
    BooksTable,
    MoveCrossBookTable,
    DownloadedBooksTable,
    UserPermissionsTable,
  )
