package proj.memorchess.axl.core.graph

import co.touchlab.kermit.Logger
import proj.memorchess.axl.core.data.DataMove
import proj.memorchess.axl.core.data.DataNode
import proj.memorchess.axl.core.data.PositionKey
import proj.memorchess.axl.core.data.book.BookQueryManager
import proj.memorchess.axl.ui.components.popup.ToastRenderer

/**
 * [TreeRepository] backed by a remote book (read-only).
 *
 * Write operations ([saveNode], [deleteMove]) are no-ops because books are read-only for clients.
 */
class BookTreeRepository(
  private val bookId: Long,
  private val bookQueryManager: BookQueryManager,
  private val toastRenderer: ToastRenderer,
) : TreeRepository {

  override suspend fun loadInto(tree: OpeningTree, trainingSchedule: TrainingSchedule?) {
    tree.clear()
    try {
      val allMoves = bookQueryManager.getBookMoves(bookId)
      allMoves.forEach { move ->
        tree
          .getOrPut(move.origin) { MutablePreviousAndNextMoves() }
          .addNextMove(
            DataMove(
              origin = move.origin,
              destination = move.destination,
              move = move.move,
              isGood = move.isGood,
            )
          )
        tree
          .getOrPut(move.destination) { MutablePreviousAndNextMoves() }
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
    // No-op — books are read-only for clients.
  }

  override suspend fun deletePosition(positionKey: PositionKey) {
    // No-op — books are read-only for clients.
  }

  override suspend fun deleteMove(origin: PositionKey, move: String) {
    // No-op — books are read-only for clients.
  }
}

private val LOGGER = Logger.withTag("BookTreeRepository")
