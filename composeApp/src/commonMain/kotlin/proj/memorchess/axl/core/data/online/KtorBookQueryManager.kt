package proj.memorchess.axl.core.data.online

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.resources.get
import io.ktor.client.plugins.resources.post
import kotlin.time.Instant
import proj.memorchess.axl.core.data.PositionKey
import proj.memorchess.axl.core.data.book.Book
import proj.memorchess.axl.core.data.book.BookMove
import proj.memorchess.axl.core.data.book.BookQueryManager
import proj.memorchess.shared.dto.BookDto
import proj.memorchess.shared.dto.BookMoveDto
import proj.memorchess.shared.routes.BooksRoute

/**
 * [BookQueryManager] implementation backed by the Ktor HTTP client and shared type-safe routes.
 *
 * @param client The pre-configured [HttpClient] that targets the MemorChess API server.
 */
class KtorBookQueryManager(private val client: HttpClient) : BookQueryManager {

  override suspend fun getBook(bookId: Long): Book? {
    val response = client.get(BooksRoute.ById(id = bookId))
    if (response.status.value == 404) return null
    return response.body<BookDto>().toBook()
  }

  override suspend fun getAllBooks(offset: Long, limit: Int, text: String): List<Book> {
    require(limit >= 0) { "Limit must be greater than 0" }
    if (limit == 0) return emptyList()
    return client
      .get(BooksRoute.Search(offset = offset, limit = limit, text = text))
      .body<List<BookDto>>()
      .map { it.toBook() }
  }

  override suspend fun getBookMoves(bookId: Long): List<BookMove> {
    return client.get(BooksRoute.Moves(id = bookId)).body<List<BookMoveDto>>().map {
      it.toBookMove()
    }
  }

  override suspend fun downloadBook(bookId: Long): List<BookMove> {
    return client.post(BooksRoute.Download(id = bookId)).body<List<BookMoveDto>>().map {
      it.toBookMove()
    }
  }
}

private fun BookDto.toBook() =
  Book(id, name, Instant.fromEpochMilliseconds(createdAt.toEpochMilliseconds()), downloads)

private fun BookMoveDto.toBookMove() =
  BookMove(PositionKey(origin), PositionKey(destination), move, isGood)
