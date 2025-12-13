@file:OptIn(ExperimentalTime::class)

package proj.memorchess.axl.core.data.online.database

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import proj.memorchess.axl.core.data.PositionIdentifier
import proj.memorchess.axl.core.data.book.Book
import proj.memorchess.axl.core.data.book.BookMove
import proj.memorchess.axl.core.data.book.BookQueryManager
import proj.memorchess.axl.core.data.book.UserPermission
import proj.memorchess.axl.core.data.online.auth.AuthManager

private const val USER_NOT_CONNECTED_MESSAGE = "User must be logged in to access book features"

/** Supabase implementation of [BookQueryManager]. */
class SupabaseBookQueryManager(
  private val client: SupabaseClient,
  private val authManager: AuthManager,
) : BookQueryManager {

  override suspend fun getAllBooks(): List<Book> {
    val result = client.postgrest.rpc("fetch_all_books").decodeList<BookFetched>()
    return result.map { it.toBook() }
  }

  override suspend fun getBookMoves(bookId: Long): List<BookMove> {
    val result =
      client.postgrest
        .rpc("fetch_book_moves", BookIdFunctionArg(bookId))
        .decodeList<BookMoveFetched>()
    return result.map { it.toBookMove() }
  }

  override suspend fun hasPermission(permission: UserPermission): Boolean {
    if (!authManager.isUserLoggedIn()) {
      return false
    }
    val result =
      client.postgrest
        .rpc("check_user_permission", CheckPermissionFunctionArg(permission.value))
        .decodeAs<Boolean>()
    return result
  }

  override suspend fun createBook(name: String): Long {
    val user = authManager.user
    checkNotNull(user) { USER_NOT_CONNECTED_MESSAGE }
    val result = client.postgrest.rpc("create_book", CreateBookFunctionArg(name)).decodeAs<Long>()
    return result
  }

  override suspend fun addMoveToBook(bookId: Long, move: BookMove): Boolean {
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

  override suspend fun removeMoveFromBook(bookId: Long, originFen: String, move: String): Boolean {
    val user = authManager.user
    checkNotNull(user) { USER_NOT_CONNECTED_MESSAGE }
    val result =
      client.postgrest
        .rpc("remove_move_from_book", RemoveMoveFromBookFunctionArg(bookId, originFen, move))
        .decodeAs<Boolean>()
    return result
  }

  override suspend fun deleteBook(bookId: Long): Boolean {
    val user = authManager.user
    checkNotNull(user) { USER_NOT_CONNECTED_MESSAGE }
    val result =
      client.postgrest.rpc("delete_book", DeleteBookFunctionArg(bookId)).decodeAs<Boolean>()
    return result
  }
}

// DTOs for Supabase responses

@Serializable
internal data class BookFetched(
  val id: Long,
  val name: String,
  @SerialName("created_at") val createdAt: Instant,
) {
  fun toBook(): Book = Book(id, name, createdAt)
}

@Serializable
internal data class BookMoveFetched(
  val origin: String,
  val destination: String,
  val move: String,
  val isGood: Boolean,
) {
  fun toBookMove(): BookMove =
    BookMove(PositionIdentifier(origin), PositionIdentifier(destination), move, isGood)
}

// Function arguments for Supabase RPC calls

@Serializable internal data class BookIdFunctionArg(@SerialName("book_id_input") val bookId: Long)

@Serializable
internal data class CheckPermissionFunctionArg(
  @SerialName("permission_input") val permission: String
)

@Serializable
internal data class CreateBookFunctionArg(@SerialName("book_name_input") val bookName: String)

@Serializable
internal data class AddMoveToBookFunctionArg(
  @SerialName("book_id_input") val bookId: Long,
  @SerialName("origin_input") val origin: String,
  @SerialName("destination_input") val destination: String,
  @SerialName("move_input") val move: String,
  @SerialName("is_good_input") val isGood: Boolean,
)

@Serializable
internal data class DeleteBookFunctionArg(@SerialName("book_id_input") val bookId: Long)

@Serializable
internal data class RemoveMoveFromBookFunctionArg(
  @SerialName("book_id_input") val bookId: Long,
  @SerialName("origin_input") val origin: String,
  @SerialName("move_input") val move: String,
)
