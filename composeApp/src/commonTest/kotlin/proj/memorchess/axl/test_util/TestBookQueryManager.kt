package proj.memorchess.axl.test_util

import proj.memorchess.axl.core.data.book.Book
import proj.memorchess.axl.core.data.book.BookMove
import proj.memorchess.axl.core.data.book.BookQueryManager
import proj.memorchess.axl.core.date.DateUtil

/**
 * In-memory [BookQueryManager] for tests.
 *
 * Provides additional write methods to seed test data that aren't part of the client-facing
 * interface.
 */
class InMemoryBookQueryManager : BookQueryManager {

  private val books = mutableMapOf<Long, Book>()
  private val moves = mutableMapOf<Long, MutableList<BookMove>>()
  private val downloads = mutableMapOf<Long, MutableSet<String>>()
  private var nextId = 1L

  /** Creates a book and returns its ID. Test-only helper. */
  fun createBook(name: String): Long {
    val id = nextId++
    books[id] = Book(id, name, DateUtil.now())
    moves[id] = mutableListOf()
    return id
  }

  /** Adds a move to a book. Test-only helper. */
  fun addMoveToBook(bookId: Long, move: BookMove) {
    moves.getOrPut(bookId) { mutableListOf() }.add(move)
  }

  override suspend fun getBook(bookId: Long): Book? = books[bookId]

  override suspend fun getAllBooks(offset: Long, limit: Int, text: String): List<Book> {
    require(limit >= 0) { "Limit must be greater than 0" }
    if (limit == 0) return emptyList()
    return books.values
      .filter { text.isBlank() || it.name.contains(text, ignoreCase = true) }
      .sortedWith(compareByDescending<Book> { it.downloads }.thenByDescending { it.createdAt })
      .drop(offset.toInt())
      .take(limit)
  }

  override suspend fun getBookMoves(bookId: Long): List<BookMove> =
    moves[bookId]?.toList() ?: emptyList()

  override suspend fun downloadBook(bookId: Long): List<BookMove> {
    val userDownloads = downloads.getOrPut(bookId) { mutableSetOf() }
    if (userDownloads.add("test-user")) {
      books[bookId]?.let { books[bookId] = it.copy(downloads = it.downloads + 1) }
    }
    return moves[bookId]?.toList() ?: emptyList()
  }

  /** Clears all data. */
  fun clear() {
    books.clear()
    moves.clear()
    downloads.clear()
    nextId = 1L
  }
}
