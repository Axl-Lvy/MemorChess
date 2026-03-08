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
import proj.memorchess.axl.core.graph.MutablePreviousAndNextMoves
import proj.memorchess.axl.core.graph.nodes.NodeManager

/**
 * BookExplorer is an interaction manager that allows exploring and downloading book moves.
 *
 * This class handles navigation through book moves and supports downloading them to the user's
 * personal repertoire.
 */
class BookExplorer(private val book: Book, val canEdit: Boolean, nodeManager: NodeManager) :
  LinesExplorer(nodeManager = nodeManager) {
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
      val mutableMoves = mutableMapOf<PositionKey, MutablePreviousAndNextMoves>()
      val depths = mutableMapOf<PositionKey, Int>()
      fillRecursively(PositionKey.START_POSITION, bookMoves, mutableMoves, depths)

      val trainingDate = PreviousAndNextDate.dummyToday()
      val dataNodes =
        mutableMoves.map { (pk, moves) ->
          DataNode(pk, moves.toImmutable(), trainingDate, depths[pk] ?: 0)
        }
      databaseQueryManager.insertNodes(*dataNodes.toTypedArray())
      toastRenderer.info("Downloaded ${bookMoves.size} moves from '${book.name}'")
    } catch (e: Exception) {
      LOGGER.e(e) { "Failed to download book '${book.name}'." }
      toastRenderer.info("Failed to download book '${book.name}'.")
    }
  }

  private fun fillRecursively(
    position: PositionKey,
    bookMoves: Map<PositionKey, List<BookMove>>,
    moves: MutableMap<PositionKey, MutablePreviousAndNextMoves>,
    depths: MutableMap<PositionKey, Int>,
  ) {
    val positionMoves = bookMoves[position] ?: return
    for (move in positionMoves) {
      val currentMoves = moves.getOrPut(position) { MutablePreviousAndNextMoves() }
      currentMoves.addNextMove(move.toDataMove())
      moves.getOrPut(move.destination) { MutablePreviousAndNextMoves() }
      val currentDepth = depths.getOrPut(position) { 0 }
      val existingDepth = depths[move.destination] ?: Int.MAX_VALUE
      val newDepth = min(existingDepth, currentDepth + 1)
      if (newDepth != existingDepth) {
        depths[move.destination] = newDepth
        fillRecursively(move.destination, bookMoves, moves, depths)
      }
    }
  }
}

private val LOGGER = Logger.withTag("BookExplorer")
