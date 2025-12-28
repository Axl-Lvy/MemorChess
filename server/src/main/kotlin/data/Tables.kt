package proj.memorchess.axl.server.data

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.CurrentTimestamp
import org.jetbrains.exposed.v1.datetime.date
import org.jetbrains.exposed.v1.datetime.timestamp

/** Exposed table definition for users. */
object Users : Table("users") {
  val id = uuid("id").autoGenerate()
  val email = varchar("email", 255).uniqueIndex()
  val passwordHash = varchar("password_hash", 255)
  val emailVerified = bool("email_verified").default(false)
  val verificationToken = varchar("verification_token", 255).nullable()
  val resetToken = varchar("reset_token", 255).nullable()
  val resetTokenExpiresAt = timestamp("reset_token_expires_at").nullable()
  val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
  val updatedAt = timestamp("updated_at").defaultExpression(CurrentTimestamp)

  override val primaryKey = PrimaryKey(id)
}

/** Exposed table definition for positions. */
object Positions : Table("positions") {
  val id = long("id").autoIncrement()
  val fenRepresentation = text("fen_representation").uniqueIndex()
  val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)

  override val primaryKey = PrimaryKey(id)
}

/** Exposed table definition for moves. */
object Moves : Table("moves") {
  val id = long("id").autoIncrement()
  val origin = reference("origin", Positions.id, onDelete = ReferenceOption.CASCADE)
  val destination = reference("destination", Positions.id, onDelete = ReferenceOption.CASCADE)
  val name = text("name")
  val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)

  override val primaryKey = PrimaryKey(id)

  init {
    uniqueIndex(origin, name)
    uniqueIndex(origin, destination)
  }
}

/** Exposed table definition for user positions. */
object UserPositions : Table("user_positions") {
  val id = long("id").autoIncrement()
  val userId = uuid("user_id").index()
  val positionId =
    reference("position_id", Positions.id, onDelete = ReferenceOption.CASCADE).index()
  val depth = integer("depth").default(0)
  val lastTrainingDate = date("last_training_date").nullable()
  val nextTrainingDate = date("next_training_date").nullable().index()
  val isDeleted = bool("is_deleted").default(false)
  val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
  val updatedAt = timestamp("updated_at").defaultExpression(CurrentTimestamp)

  override val primaryKey = PrimaryKey(id)

  init {
    uniqueIndex(userId, positionId)
  }
}

/** Exposed table definition for user moves. */
object UserMoves : Table("user_moves") {
  val id = long("id").autoIncrement()
  val userId = uuid("user_id").index()
  val moveId = reference("move_id", Moves.id, onDelete = ReferenceOption.CASCADE).index()
  val isGood = bool("is_good").default(true)
  val isDeleted = bool("is_deleted").default(false)
  val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
  val updatedAt = timestamp("updated_at").defaultExpression(CurrentTimestamp)

  override val primaryKey = PrimaryKey(id)

  init {
    uniqueIndex(userId, moveId)
  }
}

/** Exposed table definition for books. */
object Books : Table("books") {
  val id = long("id").autoIncrement()
  val name = text("name").index()
  val downloads = long("downloads").default(0).index()
  val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp).index()

  override val primaryKey = PrimaryKey(id)
}

/** Exposed table definition for move_cross_book (many-to-many relationship). */
object MoveCrossBook : Table("move_cross_book") {
  val id = long("id").autoIncrement()
  val moveId = reference("move_id", Moves.id, onDelete = ReferenceOption.CASCADE).index()
  val bookId = reference("book_id", Books.id, onDelete = ReferenceOption.CASCADE).index()
  val isGood = bool("is_good").default(true)
  val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)

  override val primaryKey = PrimaryKey(id)

  init {
    uniqueIndex(moveId, bookId)
  }
}

/** Exposed table definition for downloaded books. */
object DownloadedBooks : Table("downloaded_books") {
  val id = long("id").autoIncrement()
  val userId = uuid("user_id").index()
  val bookId = reference("book_id", Books.id, onDelete = ReferenceOption.CASCADE).index()
  val downloadedAt = timestamp("downloaded_at").defaultExpression(CurrentTimestamp)

  override val primaryKey = PrimaryKey(id)

  init {
    uniqueIndex(userId, bookId)
  }
}

/** Exposed table definition for user permissions. */
object UserPermissions : Table("user_permissions") {
  val id = long("id").autoIncrement()
  val userId = uuid("user_id").index()
  val permission = varchar("permission", 50)
  val grantedAt = timestamp("granted_at").defaultExpression(CurrentTimestamp)

  override val primaryKey = PrimaryKey(id)

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
    Users,
    Positions,
    Moves,
    UserPositions,
    UserMoves,
    Books,
    MoveCrossBook,
    DownloadedBooks,
    UserPermissions,
  )
