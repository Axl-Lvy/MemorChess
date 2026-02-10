package proj.memorchess.axl.core.interactions

import co.touchlab.kermit.Logger
import kotlin.math.min
import org.koin.core.component.inject
import proj.memorchess.axl.core.data.DataMove
import proj.memorchess.axl.core.data.DataPosition
import proj.memorchess.axl.core.data.DatabaseQueryManager
import proj.memorchess.axl.core.data.PositionIdentifier
import proj.memorchess.axl.core.data.book.Book
import proj.memorchess.axl.core.data.book.BookMove
import proj.memorchess.axl.core.data.online.database.SupabaseBookQueryManager
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
   * This converts book moves to DataMoves and DataPositions and stores them in the database.
   */
  suspend fun downloadBookToRepertoire() {
    try {
      val bookMoves = bookQueryManager.getBookMoves(book.id).groupBy { it.origin }
      bookQueryManager.registerBookDownload(book.id)
      val dataMoves = mutableListOf<DataMove>()
      val dataPositions = mutableMapOf<PositionIdentifier, DataPosition>()
      fillRecursively(PositionIdentifier.START_POSITION, bookMoves, dataMoves, dataPositions, 0)

      databaseQueryManager.insertMoves(dataMoves, dataPositions.values.toList())
      toastRenderer.info("Downloaded ${bookMoves.size} moves from '${book.name}'")
    } catch (e: Exception) {
      LOGGER.e(e) { "Failed to download book '${book.name}'." }
      toastRenderer.info("Failed to download book '${book.name}'.")
    }
  }

  private fun fillRecursively(
    position: PositionIdentifier,
    bookMoves: Map<PositionIdentifier, List<BookMove>>,
    dataMoves: MutableList<DataMove>,
    dataPositions: MutableMap<PositionIdentifier, DataPosition>,
    currentDepth: Int,
  ) {
    val moves = bookMoves[position] ?: return
    val trainingDate = PreviousAndNextDate.dummyToday()
    dataPositions.getOrPut(position) { DataPosition(position, currentDepth, trainingDate) }

    for (move in moves) {
      dataMoves.add(move.toDataMove())
      val existingDest = dataPositions[move.destination]
      val newDepth = if (existingDest != null) min(existingDest.depth, currentDepth + 1) else currentDepth + 1
      if (existingDest == null || newDepth != existingDest.depth) {
        dataPositions[move.destination] = DataPosition(move.destination, newDepth, trainingDate)
        fillRecursively(move.destination, bookMoves, dataMoves, dataPositions, newDepth)
      }
    }
  }
}

private val LOGGER = Logger.withTag("BookExplorer")
