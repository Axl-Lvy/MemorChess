package proj.memorchess.axl.core.graph.nodes

import proj.memorchess.axl.core.data.DatabaseHolder
import proj.memorchess.axl.core.data.PositionKey
import proj.memorchess.axl.core.data.StoredMove
import proj.memorchess.axl.core.data.StoredNode
import proj.memorchess.axl.core.date.PreviousAndNextDate
import proj.memorchess.axl.core.engine.Game

/**
 * Represents a node in the chess position graph. Each node corresponds to a unique chess position
 * and tracks possible moves, previous moves, and links to previous and next nodes in the graph.
 *
 * @property position The unique key representing the chess position (FEN).
 * @property linkedMoves Holds the moves available from this position and the previous moves leading
 *   to it.
 * @property previous Reference to the previous node in the graph.
 * @property next Reference to the next node in the graph.
 */
class Node(
  val position: PositionKey,
  val linkedMoves: PreviousAndNextMoves = PreviousAndNextMoves(),
  var previous: Node? = null,
  var next: Node? = null,
) {
  /**
   * Creates a new [Game] instance from this node's position.
   *
   * @return a [Game] initialized to this node's position.
   */
  fun createGame(): Game {
    return Game(position)
  }

  /**
   * Adds a child node representing a move from this position.
   *
   * @param move The move string to add.
   * @param child The child [Node] resulting from the move.
   */
  fun addChild(move: StoredMove, child: Node) {
    linkedMoves.addNextMove(move)
    next = child
  }

  /**
   * Saves this node and its ancestors to the database. Persists the position and moves, then
   * recursively saves the previous node.
   */
  suspend fun save() {
    DatabaseHolder.getDatabase()
      .insertPosition(StoredNode(position, linkedMoves, PreviousAndNextDate.dummyToday()))
    previous?.save()
  }

  /** Sets this node as [good][StoredMove.isGood] and saves it to the database. */
  suspend fun saveGood() {
    linkedMoves.setPreviousMovesAsGood(true)
    save()
  }

  /** Sets this node as [bad][StoredMove.isGood] and saves it to the database. */
  suspend fun saveBad() {
    linkedMoves.setPreviousMovesAsGood(false)
    save()
  }

  /**
   * Deletes this node and its descendants from the database. Recursively deletes child nodes and
   * clears the moves.
   */
  suspend fun delete() {
    NodeManager.clearNextMoves(position)
    linkedMoves.nextMoves.values.forEach { move ->
      val game = createGame()
      game.playMove(move.move)
      val childNode = NodeManager.createNode(game, this, move.move)
      childNode.deleteFromPrevious(move)
    }
    DatabaseHolder.getDatabase().deletePosition(position.fenRepresentation)
    linkedMoves.nextMoves.clear()
    next = null
  }

  private suspend fun deleteFromPrevious(previousMove: StoredMove) {
    println("Deleting from previous: $previousMove. Position: $position")
    NodeManager.clearPreviousMove(position, previousMove)
    check(!linkedMoves.previousMoves.contains(previousMove.move)) { "$previousMove not removed." }
    if (linkedMoves.previousMoves.isEmpty()) {
      delete()
    }
  }
}
