package proj.memorchess.axl.core.data.book

/** Interface for querying and managing books. */
interface BookQueryManager {
  /**
   * Fetches all available books.
   *
   * @return A list of all books.
   */
  suspend fun getAllBooks(): List<Book>

  /**
   * Fetches all moves in a specific book.
   *
   * @param bookId The ID of the book.
   * @return A list of moves in the book.
   */
  suspend fun getBookMoves(bookId: Long): List<BookMove>

  /**
   * Checks if the current user has a specific permission.
   *
   * @param permission The permission to check.
   * @return True if the user has the permission, false otherwise.
   */
  suspend fun hasPermission(permission: UserPermission): Boolean

  /**
   * Creates a new book. Requires BOOK_CREATION permission.
   *
   * @param name The name of the book.
   * @return The ID of the created book.
   */
  suspend fun createBook(name: String): Long

  /**
   * Adds a move to a book. Requires BOOK_CREATION permission.
   *
   * @param bookId The ID of the book.
   * @param move The move to add.
   * @return True if the move was added successfully.
   */
  suspend fun addMoveToBook(bookId: Long, move: BookMove): Boolean

  /**
   * Removes a move from a book. Requires BOOK_CREATION permission.
   *
   * @param bookId The ID of the book.
   * @param originFen The FEN representation of the origin position.
   * @param move The move name to remove.
   * @return True if the move was removed successfully.
   */
  suspend fun removeMoveFromBook(bookId: Long, originFen: String, move: String): Boolean

  /**
   * Deletes a book. Requires BOOK_CREATION permission.
   *
   * @param bookId The ID of the book to delete.
   * @return True if the book was deleted successfully.
   */
  suspend fun deleteBook(bookId: Long): Boolean
}
