package proj.memorchess.axl.core.data.book

/**
 * Read-only interface for querying community books from the server.
 *
 * Write operations (create, edit, delete) are admin-only and not exposed to the client.
 */
interface BookQueryManager {

  /**
   * Fetches a book by its ID.
   *
   * @param bookId The ID of the book to fetch.
   * @return The fetched [Book], or null if not found.
   */
  suspend fun getBook(bookId: Long): Book?

  /**
   * Fetches a paginated list of books, optionally filtered by name.
   *
   * @param offset The number of books to skip.
   * @param limit The maximum number of books to return.
   * @param text An optional text filter applied to book names (case-insensitive).
   * @return A list of [Book] objects.
   */
  suspend fun getAllBooks(offset: Long = 0, limit: Int = 50, text: String = ""): List<Book>

  /**
   * Fetches all moves for a book. For browsing only — no side effects.
   *
   * @param bookId The ID of the book.
   * @return A list of [BookMove] objects.
   */
  suspend fun getBookMoves(bookId: Long): List<BookMove>

  /**
   * Downloads a book: registers the download for the current user and returns all moves.
   *
   * This is a single network call that atomically increments the download counter (if this is the
   * user's first download) and returns the book's moves.
   *
   * @param bookId The ID of the book to download.
   * @return A list of [BookMove] objects.
   */
  suspend fun downloadBook(bookId: Long): List<BookMove>
}
