@file:OptIn(ExperimentalTime::class)

package proj.memorchess.axl.core.data.online.database

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.rpc
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import proj.memorchess.axl.core.data.PositionIdentifier
import proj.memorchess.axl.core.data.book.Book
import proj.memorchess.axl.core.data.book.BookMove
import proj.memorchess.axl.core.data.online.auth.AuthManager

private const val USER_NOT_CONNECTED_MESSAGE = "User must be logged in to access book features"

/**
 * SupabaseBookQueryManager handles book-related queries to the Supabase backend.
 *
 * This class provides methods to fetch, create, update, and delete books and their moves.
 *
 * @param client The Supabase client used for database interactions.
 * @param authManager The authentication manager to verify user permissions.
 */
class SupabaseBookQueryManager(
  private val client: SupabaseClient,
  private val authManager: AuthManager,
) {

  /**
   * Fetches a book by its ID.
   *
   * @param bookId The ID of the book to fetch.
   * @return The fetched Book object, or null if not found.
   */
  suspend fun getBook(bookId: Long): Book? {
    val result =
      client.postgrest.from("book").select { filter { eq("id", bookId) } }.decodeList<BookFetched>()
    return result.firstOrNull()?.toBook()
  }

  /**
   * Fetches all books available.
   *
   * @param offset The number of books to skip.
   * @param limit The maximum number of books to fetch.
   * @param text The text to filter books by name.
   * @return A list of Book objects.
   */
  suspend fun getAllBooks(offset: Long = 0, limit: Int = 50, text: String = ""): List<Book> {
    require(limit > 0) { "Limit must be greater than 0" }
    val result =
      client.postgrest
        .from("book")
        .select {
          if (text.isNotBlank()) {
            filter { ilike("name", "%$text%") }
          }
          order("downloads", Order.DESCENDING)
          order("created_at", Order.DESCENDING)
          order("id", Order.DESCENDING)
          range(offset, (offset + limit - 1))
        }
        .decodeList<BookFetched>()
    return result.map { it.toBook() }
  }

  /**
   * Fetches all moves associated with a specific book.
   *
   * @param bookId The ID of the book to fetch moves for.
   * @return A list of BookMove objects associated with the book.
   */
  suspend fun getBookMoves(bookId: Long): List<BookMove> {
    val result =
      client.postgrest
        .rpc("fetch_book_moves", BookIdFunctionArg(bookId))
        .decodeList<BookMoveFetched>()
    return result.map { it.toBookMove() }
  }

  /**
   * Creates a new book with the given name.
   *
   * @param name The name of the new book.
   * @return The ID of the newly created book.
   */
  suspend fun createBook(name: String): Long {
    val user = authManager.user
    checkNotNull(user) { USER_NOT_CONNECTED_MESSAGE }
    val result = client.postgrest.rpc("create_book", CreateBookFunctionArg(name)).decodeAs<Long>()
    return result
  }

  /**
   * Adds a move to a specific book.
   *
   * @param bookId The ID of the book to add the move to.
   * @param move The BookMove object representing the move to add.
   * @return True if the move was added successfully, false otherwise.
   */
  suspend fun addMoveToBook(bookId: Long, move: BookMove): Boolean {
    val user = authManager.user
    checkNotNull(user) { USER_NOT_CONNECTED_MESSAGE }
    val result =
      client.postgrest
        .rpc(
          "add_move_to_book",
          AddMoveToBookFunctionArg(
            bookId,
            move.origin.fenRepresentation,
            move.destination.fenRepresentation,
            move.move,
            move.isGood,
          ),
        )
        .decodeAs<Boolean>()
    return result
  }

  /**
   * Removes a move from a specific book.
   *
   * @param bookId The ID of the book to remove the move from.
   * @param originFen The FEN representation of the origin position.
   * @param move The move string to remove.
   * @return True if the move was removed successfully, false otherwise.
   */
  suspend fun removeMoveFromBook(bookId: Long, originFen: String, move: String): Boolean {
    val user = authManager.user
    checkNotNull(user) { USER_NOT_CONNECTED_MESSAGE }
    val result =
      client.postgrest
        .rpc("remove_move_from_book", RemoveMoveFromBookFunctionArg(bookId, originFen, move))
        .decodeAs<Boolean>()
    return result
  }

  /**
   * Deletes a book by its ID.
   *
   * @param bookId The ID of the book to delete.
   * @return True if the book was deleted successfully, false otherwise.
   */
  suspend fun deleteBook(bookId: Long): Boolean {
    val user = authManager.user
    checkNotNull(user) { USER_NOT_CONNECTED_MESSAGE }
    val result =
      client.postgrest.rpc("delete_book", DeleteBookFunctionArg(bookId)).decodeAs<Boolean>()
    return result
  }

  /**
   * Registers a book download for the current user.
   *
   * This creates a downloaded_books record and increments the book's download counter if the book
   * hasn't been downloaded by this user before.
   *
   * @param bookId The ID of the book to register as downloaded.
   * @return True if the download was registered (first time), false if already downloaded.
   */
  suspend fun registerBookDownload(bookId: Long): Boolean {
    val user = authManager.user
    checkNotNull(user) { USER_NOT_CONNECTED_MESSAGE }
    val result =
      client.postgrest.rpc("register_book_download", BookIdFunctionArg(bookId)).decodeAs<Boolean>()
    return result
  }
}

// DTOs for Supabase responses

@Serializable
private data class BookFetched(
  val id: Long,
  val name: String,
  @SerialName("created_at") val createdAt: Instant,
  val downloads: Int = 0,
) {
  fun toBook(): Book = Book(id, name, createdAt, downloads)
}

@Serializable
private data class BookMoveFetched(
  val origin: String,
  val destination: String,
  val move: String,
  val isGood: Boolean,
) {
  fun toBookMove(): BookMove =
    BookMove(PositionIdentifier(origin), PositionIdentifier(destination), move, isGood)
}

// Function arguments for Supabase RPC calls

@Serializable private data class BookIdFunctionArg(@SerialName("book_id_input") val bookId: Long)

@Serializable
private data class CreateBookFunctionArg(@SerialName("book_name_input") val bookName: String)

@Serializable
private data class AddMoveToBookFunctionArg(
  @SerialName("book_id_input") val bookId: Long,
  @SerialName("origin_input") val origin: String,
  @SerialName("destination_input") val destination: String,
  @SerialName("move_input") val move: String,
  @SerialName("is_good_input") val isGood: Boolean,
)

@Serializable
private data class DeleteBookFunctionArg(@SerialName("book_id_input") val bookId: Long)

@Serializable
private data class RemoveMoveFromBookFunctionArg(
  @SerialName("book_id_input") val bookId: Long,
  @SerialName("origin_input") val origin: String,
  @SerialName("move_input") val move: String,
)
