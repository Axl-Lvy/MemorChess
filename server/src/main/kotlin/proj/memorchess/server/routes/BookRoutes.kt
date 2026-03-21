package proj.memorchess.server.routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.resources.get
import io.ktor.server.resources.post
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import kotlinx.datetime.Instant
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.like
import org.jetbrains.exposed.sql.SqlExpressionBuilder.plus
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.intLiteral
import org.jetbrains.exposed.sql.lowerCase
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import proj.memorchess.server.db.BookTable
import proj.memorchess.server.db.DownloadedBooksTable
import proj.memorchess.server.db.MoveCrossBookTable
import proj.memorchess.server.db.MovesTable
import proj.memorchess.server.db.PositionsTable
import proj.memorchess.server.plugins.userId
import proj.memorchess.shared.dto.BookDto
import proj.memorchess.shared.dto.BookMoveDto
import proj.memorchess.shared.routes.BooksRoute

/** Registers read-only book endpoints. */
fun Route.bookRoutes() {

  get<BooksRoute.Search> { params ->
    if (params.limit < 0) {
      call.respondText("Limit must be >= 0", status = HttpStatusCode.BadRequest)
      return@get
    }
    val books = transaction {
      BookTable.selectAll()
        .apply {
          if (params.text.isNotBlank()) {
            adjustWhere { BookTable.name.lowerCase() like "%${params.text.lowercase()}%" }
          }
        }
        .orderBy(BookTable.downloads to SortOrder.DESC)
        .orderBy(BookTable.createdAt to SortOrder.DESC)
        .orderBy(BookTable.id to SortOrder.DESC)
        .limit(params.limit)
        .offset(params.offset)
        .map { row ->
          BookDto(
            id = row[BookTable.id].value,
            name = row[BookTable.name],
            createdAt =
              Instant.fromEpochMilliseconds(row[BookTable.createdAt].toInstant().toEpochMilli()),
            downloads = row[BookTable.downloads],
          )
        }
    }
    call.respond(books)
  }

  get<BooksRoute.ById> { params ->
    val book = transaction {
      BookTable.selectAll()
        .where { BookTable.id eq params.id }
        .firstOrNull()
        ?.let { row ->
          BookDto(
            id = row[BookTable.id].value,
            name = row[BookTable.name],
            createdAt =
              Instant.fromEpochMilliseconds(row[BookTable.createdAt].toInstant().toEpochMilli()),
            downloads = row[BookTable.downloads],
          )
        }
    }
    if (book == null) {
      call.respondText("Book not found", status = HttpStatusCode.NotFound)
    } else {
      call.respond(book)
    }
  }

  get<BooksRoute.Moves> { params ->
    val moves = fetchBookMoves(params.id)
    call.respond(moves)
  }

  post<BooksRoute.Download> { params ->
    val currentUserId = call.userId
    val bookExists = transaction { BookTable.selectAll().where { BookTable.id eq params.id }.any() }
    if (!bookExists) {
      call.respondText("Book not found", status = HttpStatusCode.NotFound)
      return@post
    }

    transaction {
      val inserted =
        DownloadedBooksTable.insertIgnore {
          it[userId] = currentUserId
          it[bookId] = params.id
        }
      if (inserted.insertedCount > 0) {
        BookTable.update({ BookTable.id eq params.id }) {
          it[downloads] = downloads plus intLiteral(1)
        }
      }
    }

    val moves = fetchBookMoves(params.id)
    call.respond(moves)
  }
}

/**
 * Fetches all moves for a book by joining move_cross_book, moves, and positions tables.
 *
 * Equivalent to the old Supabase `fetch_book_moves` RPC.
 */
private fun fetchBookMoves(bookId: Long): List<BookMoveDto> = transaction {
  MoveCrossBookTable.join(MovesTable, JoinType.INNER, MoveCrossBookTable.moveId, MovesTable.id)
    .join(
      PositionsTable,
      JoinType.INNER,
      MovesTable.origin,
      PositionsTable.id,
      additionalConstraint = null,
    )
    .selectAll()
    .where { MoveCrossBookTable.bookId eq bookId }
    .map { row ->
      val originFen = row[PositionsTable.fenRepresentation]
      val destinationId = row[MovesTable.destination]
      val destinationFen =
        PositionsTable.selectAll()
          .where { PositionsTable.id eq destinationId }
          .first()[PositionsTable.fenRepresentation]
      BookMoveDto(
        origin = originFen,
        destination = destinationFen,
        move = row[MovesTable.name],
        isGood = row[MoveCrossBookTable.isGood],
      )
    }
}
