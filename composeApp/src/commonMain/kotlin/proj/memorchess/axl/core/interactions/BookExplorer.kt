package proj.memorchess.axl.core.interactions

import org.koin.core.component.inject
import proj.memorchess.axl.core.data.DataMove
import proj.memorchess.axl.core.data.DataNode
import proj.memorchess.axl.core.data.DatabaseQueryManager
import proj.memorchess.axl.core.data.PositionIdentifier
import proj.memorchess.axl.core.data.book.Book
import proj.memorchess.axl.core.data.online.database.SupabaseBookQueryManager
import proj.memorchess.axl.core.date.DateUtil
import proj.memorchess.axl.core.date.PreviousAndNextDate
import proj.memorchess.axl.core.graph.nodes.IsolatedBookNode
import proj.memorchess.axl.core.graph.nodes.NodeManager
import proj.memorchess.axl.core.graph.nodes.PreviousAndNextMoves

/**
 * BookExplorer is an interaction manager that allows exploring and downloading book moves.
 *
 * This class handles navigation through book moves and supports downloading them to the user's
 * personal repertoire.
 */
class BookExplorer(
  private val book: Book,
  val canEdit: Boolean,
  nodeManager: NodeManager<IsolatedBookNode>,
) : LinesExplorer<IsolatedBookNode>(nodeManager = nodeManager) {
  private val bookQueryManager: SupabaseBookQueryManager by inject()
  private val databaseQueryManager: DatabaseQueryManager by inject()

  init {
    if (!canEdit) {
      block()
    }
  }

  /**
   * Downloads all moves from the book to the user's repertoire.
   *
   * This converts book moves to DataNodes and stores them in the database.
   */
  suspend fun downloadBookToRepertoire() {
    val positionsToSave = mutableMapOf<PositionIdentifier, MutableList<DataMove>>()
    val bookMoves = bookQueryManager.getBookMoves(book.id)

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
    nodeManager.resetCacheFromSource()
    toastRenderer.info("Downloaded ${bookMoves.size} moves from '${book.name}'")
  }
}
