package proj.memorchess.server.db

import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.timestampWithTimeZone

/** The `app_user` table — users identified by client-generated UUID. */
object AppUserTable : Table("app_user") {
  val id = uuid("id")
  val createdAt = timestampWithTimeZone("created_at")

  override val primaryKey = PrimaryKey(id)
}

/** The `book` table — community opening books. */
object BookTable : LongIdTable("book") {
  val name = text("name")
  val createdAt = timestampWithTimeZone("created_at")
  val downloads = integer("downloads").default(0)
}

/** The `positions` table — unique FEN positions. */
object PositionsTable : LongIdTable("positions") {
  val fenRepresentation = text("fen_representation").uniqueIndex()
}

/** The `moves` table — moves between two positions. */
object MovesTable : LongIdTable("moves") {
  val origin = long("origin").references(PositionsTable.id)
  val destination = long("destination").references(PositionsTable.id)
  val name = text("name")

  init {
    uniqueIndex(origin, destination, name)
  }
}

/** The `move_cross_book` join table — which moves belong to which books. */
object MoveCrossBookTable : Table("move_cross_book") {
  val moveId = long("move_id").references(MovesTable.id)
  val bookId = long("book_id").references(BookTable.id)
  val isGood = bool("is_good")

  override val primaryKey = PrimaryKey(moveId, bookId)
}

/** The `downloaded_books` table — tracks which users have downloaded which books. */
object DownloadedBooksTable : Table("downloaded_books") {
  val userId = uuid("user_id").references(AppUserTable.id)
  val bookId = long("book_id").references(BookTable.id)

  override val primaryKey = PrimaryKey(userId, bookId)
}
