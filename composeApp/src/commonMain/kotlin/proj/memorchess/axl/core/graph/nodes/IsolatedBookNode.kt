package proj.memorchess.axl.core.graph.nodes

import org.koin.core.component.inject
import org.koin.core.parameter.parametersOf
import org.koin.core.qualifier.named
import proj.memorchess.axl.core.data.PositionIdentifier
import proj.memorchess.axl.core.data.book.BookMove
import proj.memorchess.axl.core.data.online.database.SupabaseBookQueryManager

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
  private var isSaved: Boolean = false
  override val nodeManager: NodeManager<IsolatedBookNode> by
    inject(named("book")) { parametersOf(bookId) }

  override suspend fun save() {
    if (isSaved) return
    previousAndNextMoves.filterValidMoves().previousMoves.forEach { move ->
      val isGood = move.value.isGood
      checkNotNull(isGood) { "isGood must be defined to save book moves" }
      bookQueryManager.addMoveToBook(
        bookId,
        BookMove(move.value.origin, move.value.destination, move.value.move, isGood),
      )
    }
    isSaved = true
  }

  override suspend fun delete() {
    previousAndNextMoves.nextMoves.values.forEach { move ->
      val game = createGame()
      game.playMove(move.move)
      val childNode = nodeManager.createNode(game, this, move.move)
      childNode.deleteFromPrevious(move)
      bookQueryManager.removeMoveFromBook(bookId, position.fenRepresentation, move.move)
    }
    nodeManager.clearNextMoves(position)
    previousAndNextMoves.nextMoves.clear()
    next = null
  }
}
