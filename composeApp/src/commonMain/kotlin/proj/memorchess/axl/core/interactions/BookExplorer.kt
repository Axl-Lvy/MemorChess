package proj.memorchess.axl.core.interactions

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.koin.core.component.inject
import proj.memorchess.axl.core.data.DataMove
import proj.memorchess.axl.core.data.DataNode
import proj.memorchess.axl.core.data.DatabaseQueryManager
import proj.memorchess.axl.core.data.PositionIdentifier
import proj.memorchess.axl.core.data.book.Book
import proj.memorchess.axl.core.data.book.BookMove
import proj.memorchess.axl.core.data.book.BookQueryManager
import proj.memorchess.axl.core.date.DateUtil
import proj.memorchess.axl.core.date.PreviousAndNextDate
import proj.memorchess.axl.core.engine.Game
import proj.memorchess.axl.core.graph.nodes.PreviousAndNextMoves

/**
 * BookExplorer is an interaction manager that allows exploring and downloading book moves.
 *
 * This class handles navigation through book moves and supports downloading them to the user's
 * personal repertoire.
 */
class BookExplorer(private val book: Book) : InteractionsManager(Game()) {

  private val bookQueryManager: BookQueryManager by inject()
  private val databaseQueryManager: DatabaseQueryManager by inject()

  /** All moves in the current book. */
  private var bookMoves: List<BookMove> = emptyList()

  /** Current position identifier. */
  var currentPosition by mutableStateOf(PositionIdentifier.START_POSITION)
    private set

  /** Available next moves from current position within the book. */
  var availableNextMoves by mutableStateOf<List<BookMove>>(emptyList())
    private set

  /** Move history for back navigation. */
  private val moveHistory = mutableListOf<BookMove>()

  /** Loads the book moves from the database. */
  suspend fun loadBookMoves() {
    bookMoves = bookQueryManager.getBookMoves(book.id)
    updateAvailableMoves()
  }

  /** Updates the available next moves based on current position. */
  private fun updateAvailableMoves() {
    availableNextMoves = bookMoves.filter { it.origin == currentPosition }
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
   * @return A list of move names available in the book from current position.
   */
  fun getNextMoves(): List<String> {
    return availableNextMoves.map { it.move }.sorted()
  }

  override suspend fun afterPlayMove(move: String) {
    val bookMove = availableNextMoves.find { it.move == move }
    if (bookMove != null) {
      moveHistory.add(bookMove)
      currentPosition = bookMove.destination
      updateAvailableMoves()
    }
    callCallBacks()
  }

  /**
   * Downloads all moves from the book to the user's repertoire.
   *
   * This converts book moves to DataNodes and stores them in the database.
   */
  suspend fun downloadBookToRepertoire() {
    val positionsToSave = mutableMapOf<PositionIdentifier, MutableList<DataMove>>()

    bookMoves.forEach { bookMove ->
      val dataMove =
        DataMove(
          origin = bookMove.origin,
          destination = bookMove.destination,
          move = bookMove.move,
          isGood = bookMove.isGood,
          updatedAt = DateUtil.now(),
        )
      positionsToSave.getOrPut(bookMove.origin) { mutableListOf() }.add(dataMove)
    }

    val today = DateUtil.today()
    val trainingDate = PreviousAndNextDate(today, today)

    val dataNodes =
      positionsToSave.map { (position, moves) ->
        val previousMoves =
          bookMoves
            .filter { it.destination == position }
            .map { DataMove(it.origin, it.destination, it.move, it.isGood) }
        DataNode(
          positionIdentifier = position,
          previousAndNextMoves = PreviousAndNextMoves(previousMoves, moves, depth = 0),
          previousAndNextTrainingDate = trainingDate,
          updatedAt = DateUtil.now(),
        )
      }

    databaseQueryManager.insertNodes(*dataNodes.toTypedArray())
    nodeManager.resetCacheFromDataBase()
    toastRenderer.info("Downloaded ${bookMoves.size} moves from '${book.name}'")
  }
}
