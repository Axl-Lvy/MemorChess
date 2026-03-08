package proj.memorchess.axl.core.interactions

import co.touchlab.kermit.Logger
import kotlin.math.min
import org.koin.core.component.inject
import proj.memorchess.axl.core.data.DataNode
import proj.memorchess.axl.core.data.DatabaseQueryManager
import proj.memorchess.axl.core.data.PositionKey
import proj.memorchess.axl.core.data.book.Book
import proj.memorchess.axl.core.data.book.BookMove
import proj.memorchess.axl.core.data.online.database.SupabaseBookQueryManager
import proj.memorchess.axl.core.date.PreviousAndNextDate
import proj.memorchess.axl.core.graph.PreviousAndNextMoves
import proj.memorchess.axl.core.graph.TreeRepository
import proj.memorchess.axl.core.graph.nodes.NodeManager

/**
 * BookExplorer is an interaction manager that allows exploring and downloading book moves.
 *
 * This class handles navigation through book moves and supports downloading them to the user's
 * personal repertoire.
 */
class BookExplorer(
  private val book: Book,
  val canEdit: Boolean,
  nodeManager: NodeManager,
  treeRepository: TreeRepository,
) : LinesExplorer(nodeManager = nodeManager, treeRepository = treeRepository) {
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
    try {
      val bookMoves = bookQueryManager.getBookMoves(book.id).groupBy { it.origin }
      bookQueryManager.registerBookDownload(book.id)
      val dataNodes = mutableMapOf<PositionKey, DataNode>()
      dataNodes.fillRecursively(PositionKey.START_POSITION, bookMoves)

      databaseQueryManager.insertNodes(*dataNodes.values.toTypedArray())
      toastRenderer.info("Downloaded ${bookMoves.size} moves from '${book.name}'")
    } catch (e: Exception) {
      LOGGER.e(e) { "Failed to download book '${book.name}'." }
      toastRenderer.info("Failed to download book '${book.name}'.")
    }
  }

  private fun MutableMap<PositionKey, DataNode>.fillRecursively(
    position: PositionKey,
    bookMoves: Map<PositionKey, List<BookMove>>,
  ) {
    val moves = bookMoves[position] ?: return
    val trainingDate = PreviousAndNextDate.dummyToday()
    for (move in moves) {
      val currentPreviousAndNextMoves =
        this.getOrPut(position) { DataNode(position, PreviousAndNextMoves(), trainingDate) }
          .previousAndNextMoves
      currentPreviousAndNextMoves.addNextMove(move.toDataMove())
      val nextPreviousAndNextMoves =
        this.getOrPut(move.destination) {
            DataNode(move.destination, PreviousAndNextMoves(depth = Int.MAX_VALUE), trainingDate)
          }
          .previousAndNextMoves
      val newDepth = min(nextPreviousAndNextMoves.depth, currentPreviousAndNextMoves.depth + 1)
      if (newDepth != nextPreviousAndNextMoves.depth) {
        nextPreviousAndNextMoves.depth = newDepth
        this.fillRecursively(move.destination, bookMoves)
      }
    }
  }
}

private val LOGGER = Logger.withTag("BookExplorer")
