package proj.memorchess.axl.core.graph.nodes

import org.koin.core.component.inject
import org.koin.core.parameter.parametersOf
import org.koin.core.qualifier.named
import proj.memorchess.axl.core.data.DataMove
import proj.memorchess.axl.core.data.PositionIdentifier
import proj.memorchess.axl.core.data.book.BookMove
import proj.memorchess.axl.core.data.online.database.SupabaseBookQueryManager

class IsolatedBookNode(
  private val bookId: Long,
  position: PositionIdentifier,
  previousAndNextMoves: PreviousAndNextMoves = PreviousAndNextMoves(),
  previous: IsolatedBookNode? = null,
  next: IsolatedBookNode? = null,
) : Node<IsolatedBookNode>(position, previousAndNextMoves, previous, next) {
  private val bookQueryManager: SupabaseBookQueryManager by inject()
  private var isSaved: Boolean = false
  private val nodeManager: NodeManager<IsolatedBookNode> by
    inject(named("booked")) { parametersOf(bookId) }

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

  private suspend fun deleteFromPrevious(previousMove: DataMove) {
    println("Deleting from previous: $previousMove. Position: $position")
    nodeManager.clearPreviousMove(position, previousMove)
    check(!previousAndNextMoves.previousMoves.contains(previousMove.move)) {
      "$previousMove not removed."
    }
    if (previousAndNextMoves.previousMoves.isEmpty()) {
      delete()
    }
  }

  override fun calculateNumberOfNodesToDelete(previousMove: DataMove?): Int {
    return if (
      previousMove != null &&
        previousAndNextMoves.previousMoves.values.any { it.move != previousMove.move }
    ) {
      0
    } else {
      var count = 1
      previousAndNextMoves.nextMoves.values.forEach { move ->
        val game = createGame()
        game.playMove(move.move)
        val childNode = nodeManager.createNode(game, this, move.move)
        count += childNode.calculateNumberOfNodesToDelete(move)
      }
      count
    }
  }
}
