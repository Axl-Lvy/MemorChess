package proj.memorchess.axl.core.interactions

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
import proj.memorchess.axl.core.graph.nodes.Node
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

  private var node = createInitialNode()

  init {
    block()
  }

  private fun createInitialNode(): Node {
    return nodeManager.createNodeFromBook(bookMoves)
  }

  /** Loads the book moves from the database. */
  suspend fun loadBookMoves() {
    bookMoves = bookQueryManager.getBookMoves(book.id)
    node = createInitialNode()
    reset(node.position)
  }

  override suspend fun afterPlayMove(move: String) {
    val childNode = node.getChild(move) ?: return
    node = childNode
    callCallBacks()
  }

  /** Moves back in the exploration tree to the previous node. */
  fun back() {
    val parent = node.previous
    if (parent != null) {
      node = parent
    }
    game = node.createGame()
    callCallBacks(false)
  }

  fun getNextMoves(): List<String> {
    return node.previousAndNextMoves.nextMoves.values.map { it.move }
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
