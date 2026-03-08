package proj.memorchess.axl.core.graph

import co.touchlab.kermit.Logger
import proj.memorchess.axl.core.data.DataMove
import proj.memorchess.axl.core.data.DataNode
import proj.memorchess.axl.core.data.PositionKey
import proj.memorchess.axl.core.data.book.BookMove
import proj.memorchess.axl.core.data.online.database.SupabaseBookQueryManager
import proj.memorchess.axl.ui.components.popup.ToastRenderer

/** [TreeRepository] backed by a Supabase book. */
class BookTreeRepository(
  private val bookId: Long,
  private val bookQueryManager: SupabaseBookQueryManager,
  private val toastRenderer: ToastRenderer,
) : TreeRepository {

  override suspend fun loadInto(tree: OpeningTree, trainingSchedule: TrainingSchedule?) {
    tree.clear()
    try {
      val allMoves = bookQueryManager.getBookMoves(bookId)
      allMoves.forEach { move ->
        tree
          .getOrPut(move.origin) { PreviousAndNextMoves() }
          .addNextMove(
            DataMove(
              origin = move.origin,
              destination = move.destination,
              move = move.move,
              isGood = move.isGood,
            )
          )
        tree
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
    } catch (e: Exception) {
      LOGGER.e(e) { "Failed to fetch book moves from $bookId" }
      toastRenderer.info("Failed to fetch book moves.")
    }
  }

  override suspend fun saveNode(node: DataNode) {
    node.previousAndNextMoves.filterValidMoves().previousMoves.forEach { move ->
      val isGood = move.value.isGood
      checkNotNull(isGood) { "isGood must be defined to save book moves" }
      try {
        bookQueryManager.addMoveToBook(
          bookId,
          BookMove(move.value.origin, move.value.destination, move.value.move, isGood),
        )
      } catch (e: Exception) {
        LOGGER.e(e) { "Failed to add move to book" }
        toastRenderer.info("Failed to add move ${move.value.move} to book")
      }
    }
  }

  override suspend fun deletePosition(positionKey: PositionKey) {
    // Not applicable for books — moves are deleted individually
  }

  override suspend fun deleteMove(origin: PositionKey, move: String) {
    try {
      bookQueryManager.removeMoveFromBook(bookId, origin, move)
    } catch (e: Exception) {
      LOGGER.e(e) { "Failed to delete move $move from book." }
      toastRenderer.info("Failed to delete move $move from book.")
    }
  }
}

private val LOGGER = Logger.withTag("BookTreeRepository")
