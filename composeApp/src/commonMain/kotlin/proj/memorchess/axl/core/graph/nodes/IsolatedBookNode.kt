package proj.memorchess.axl.core.graph.nodes

import co.touchlab.kermit.Logger
import org.koin.core.component.inject
import org.koin.core.parameter.parametersOf
import org.koin.core.qualifier.named
import proj.memorchess.axl.core.data.PositionIdentifier
import proj.memorchess.axl.core.data.book.BookMove
import proj.memorchess.axl.core.data.online.database.SupabaseBookQueryManager
import proj.memorchess.axl.ui.components.popup.ToastRenderer

/**
 * IsolatedBookNode represents a node in a chess opening book that is isolated from the user's
 * personal repertoire.
 *
 * This node interacts with a specific book in the online database to save and delete moves.
 *
 * @param bookId The ID of the book this node belongs to.
 * @param position The position identifier for this node.
 * @param previousAndNextMoves The previous and next moves associated with this node.
 * @param previous The previous node in the sequence.
 * @param next The next node in the sequence.
 */
class IsolatedBookNode(
  private val bookId: Long,
  position: PositionIdentifier,
  previousAndNextMoves: PreviousAndNextMoves = PreviousAndNextMoves(),
  previous: IsolatedBookNode? = null,
  next: IsolatedBookNode? = null,
) : Node<IsolatedBookNode>(position, previousAndNextMoves, previous, next) {
  private val bookQueryManager: SupabaseBookQueryManager by inject()
  private val toastRenderer: ToastRenderer by inject()
  private var isSaved: Boolean = false
  override val nodeManager: NodeManager<IsolatedBookNode> by
    inject(named("book")) { parametersOf(bookId) }

  override suspend fun save() {
    if (isSaved) return
    previousAndNextMoves.filterValidMoves().previousMoves.forEach { move ->
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
    isSaved = true
  }

  override suspend fun delete() {
    previousAndNextMoves.nextMoves.values.forEach { move ->
      val engine = createEngine()
      engine.playSanMove(move.move)
      val childNode = nodeManager.createNode(engine, this, move.move)
      childNode.deleteFromPrevious(move)
      try {
        bookQueryManager.removeMoveFromBook(bookId, position.fenRepresentation, move.move)
      } catch (e: Exception) {
        toastRenderer.info("Failed to remove move ${move.move}.")
        LOGGER.e(e) { "Failed to remove move from book" }
      }
    }
    nodeManager.clearNextMoves(position)
    previousAndNextMoves.nextMoves.clear()
    next = null
  }
}

private val LOGGER = Logger.withTag("IsolatedBookNode")
