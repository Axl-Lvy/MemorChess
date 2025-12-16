package proj.memorchess.axl.core.graph.nodes

import co.touchlab.kermit.Logger
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import proj.memorchess.axl.core.data.DataMove
import proj.memorchess.axl.core.data.DataNode
import proj.memorchess.axl.core.data.PositionIdentifier
import proj.memorchess.axl.core.data.online.database.SupabaseBookQueryManager

/**
 * BookBasedNodeCache is a NodeCache implementation that retrieves moves from a specific book in the
 * online database.
 *
 * @param bookId The ID of the book to retrieve moves from.
 */
class BookBasedNodeCache(private val bookId: Long) : NodeCache(), KoinComponent {

  private val bookQueryManager: SupabaseBookQueryManager by inject()

  override suspend fun resetFromSource() {
    clear()
    val allMoves = bookQueryManager.getBookMoves(bookId)
    allMoves.forEach { move ->
      movesCache
        .getOrPut(move.origin) { PreviousAndNextMoves() }
        .addNextMove(
          DataMove(
            origin = move.origin,
            destination = move.destination,
            move = move.move,
            isGood = move.isGood,
          )
        )
      movesCache
        .getOrPut(move.destination) { PreviousAndNextMoves() }
        .addPreviousMove(
          DataMove(
            origin = move.origin,
            destination = move.destination,
            move = move.move,
            isGood = move.isGood,
          )
        )
      LOGGER.i { "Retrieved move: $move" }
    }
  }

  override fun getNodeFromDay(day: Int): DataNode? {
    throwUnsupportedOperation()
  }

  override fun getNodeToTrainAfterPosition(
    day: Int,
    positionIdentifier: PositionIdentifier,
  ): DataNode? {
    throwUnsupportedOperation()
  }

  override fun getNumberOfNodesToTrain(day: Int): Int {
    throwUnsupportedOperation()
  }

  override fun cacheNode(node: DataNode) {
    throwUnsupportedOperation()
  }

  override suspend fun deleteMove(move: DataMove) {
    bookQueryManager.removeMoveFromBook(bookId, move.origin.fenRepresentation, move.move)
  }

  private fun throwUnsupportedOperation(): Nothing {
    throw UnsupportedOperationException("Operation not supported in BookBasedNodeCache")
  }

  private fun clear() {
    movesCache.clear()
  }
}

private val LOGGER = Logger.withTag("BookBasedNodeCache")
