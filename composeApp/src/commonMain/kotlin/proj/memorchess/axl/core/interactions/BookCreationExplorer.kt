package proj.memorchess.axl.core.interactions

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.koin.core.component.inject
import proj.memorchess.axl.core.data.PositionIdentifier
import proj.memorchess.axl.core.data.book.Book
import proj.memorchess.axl.core.data.book.BookMove
import proj.memorchess.axl.core.data.book.BookQueryManager
import proj.memorchess.axl.core.engine.Game

/**
 * BookCreationExplorer is an interaction manager for creating and editing books.
 *
 * This class is only available to users with BOOK_CREATION permission. It allows adding moves to a
 * book without affecting the user's personal repertoire.
 */
class BookCreationExplorer(private var book: Book? = null) : InteractionsManager(Game()) {

  private val bookQueryManager: BookQueryManager by inject()

  /** All moves currently in the book. */
  private val addedMoves = mutableListOf<BookMove>()

  /** Current position identifier. */
  var currentPosition by mutableStateOf(PositionIdentifier.START_POSITION)
    private set

  /** Move history for back navigation. */
  private val moveHistory = mutableListOf<BookMove>()

  /** Available next moves from current position within the book being created. */
  var availableNextMoves by mutableStateOf<List<BookMove>>(emptyList())
    private set

  /** Whether the current position has been saved to the book. */
  var isCurrentMoveSaved by mutableStateOf(false)
    private set

  /**
   * Creates a new book with the given name.
   *
   * @param name The name of the book.
   * @return The created book.
   */
  suspend fun createBook(name: String): Book {
    val bookId = bookQueryManager.createBook(name)
    val createdBook = Book(bookId, name, proj.memorchess.axl.core.date.DateUtil.now())
    book = createdBook
    return createdBook
  }

  /**
   * Loads an existing book for editing.
   *
   * @param existingBook The book to edit.
   */
  suspend fun loadBook(existingBook: Book) {
    book = existingBook
    val moves = bookQueryManager.getBookMoves(existingBook.id)
    addedMoves.clear()
    addedMoves.addAll(moves)
    updateAvailableMoves()
  }

  /** Updates the available next moves based on current position. */
  private fun updateAvailableMoves() {
    availableNextMoves = addedMoves.filter { it.origin == currentPosition }
    isCurrentMoveSaved =
      moveHistory.isNotEmpty() &&
        addedMoves.any {
          it.origin == moveHistory.last().origin &&
            it.destination == moveHistory.last().destination &&
            it.move == moveHistory.last().move
        }
  }

  /** Resets the explorer to the starting position. */
  fun reset() {
    currentPosition = PositionIdentifier.START_POSITION
    game = Game()
    moveHistory.clear()
    updateAvailableMoves()
    callCallBacks(false)
  }

  /** Moves back to the previous position. */
  fun back() {
    val lastMove = moveHistory.removeLastOrNull()
    if (lastMove != null) {
      currentPosition = lastMove.origin
      game = Game(currentPosition)
      updateAvailableMoves()
      callCallBacks(false)
    } else {
      toastRenderer.info("No previous move.")
    }
  }

  /**
   * Gets the list of move names that can be played from the current position.
   *
   * @return A list of move names added to the book from current position.
   */
  fun getNextMoves(): List<String> {
    return availableNextMoves.map { it.move }.sorted()
  }

  override suspend fun afterPlayMove(move: String) {
    val existingMove = availableNextMoves.find { it.move == move }
    if (existingMove != null) {
      moveHistory.add(existingMove)
      currentPosition = existingMove.destination
    } else {
      val newMove =
        BookMove(
          origin = currentPosition,
          destination = game.position.createIdentifier(),
          move = move,
          isGood = true,
        )
      moveHistory.add(newMove)
      currentPosition = newMove.destination
    }
    updateAvailableMoves()
    callCallBacks()
  }

  /** Saves the current move to the book as a good move. */
  suspend fun saveCurrentMoveAsGood() {
    saveCurrentMove(true)
  }

  /** Saves the current move to the book as a bad move (opponent's mistake). */
  suspend fun saveCurrentMoveAsBad() {
    saveCurrentMove(false)
  }

  private suspend fun saveCurrentMove(isGood: Boolean) {
    val currentBook = book
    if (currentBook == null) {
      toastRenderer.info("Please create a book first")
      return
    }

    val lastMove = moveHistory.lastOrNull()
    if (lastMove == null) {
      toastRenderer.info("No move to save")
      return
    }

    val moveToSave = BookMove(lastMove.origin, lastMove.destination, lastMove.move, isGood)
    val success = bookQueryManager.addMoveToBook(currentBook.id, moveToSave)

    if (success) {
      addedMoves.removeAll { it.origin == moveToSave.origin && it.move == moveToSave.move }
      addedMoves.add(moveToSave)
      updateAvailableMoves()
      toastRenderer.info("Move saved")
    } else {
      toastRenderer.info("Failed to save move")
    }
  }

  /** Deletes the current book. */
  suspend fun deleteCurrentBook() {
    val currentBook = book ?: return
    bookQueryManager.deleteBook(currentBook.id)
    book = null
    addedMoves.clear()
    reset()
    toastRenderer.info("Book '${currentBook.name}' deleted")
  }

  /**
   * Deletes the current move from the book.
   *
   * This removes the move that led to the current position.
   */
  suspend fun deleteCurrentMove() {
    val currentBook = book
    if (currentBook == null) {
      toastRenderer.info("No book selected")
      return
    }

    val lastMove = moveHistory.lastOrNull()
    if (lastMove == null) {
      toastRenderer.info("No move to delete")
      return
    }

    val success =
      bookQueryManager.removeMoveFromBook(
        currentBook.id,
        lastMove.origin.fenRepresentation,
        lastMove.move,
      )

    if (success) {
      addedMoves.removeAll { it.origin == lastMove.origin && it.move == lastMove.move }
      back()
      toastRenderer.info("Move deleted")
    } else {
      toastRenderer.info("Failed to delete move")
    }
  }

  /** Gets the current book. */
  fun getCurrentBook(): Book? = book
}
