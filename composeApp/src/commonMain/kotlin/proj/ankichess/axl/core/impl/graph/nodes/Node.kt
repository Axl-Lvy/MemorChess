package proj.ankichess.axl.core.impl.graph.nodes

import proj.ankichess.axl.core.impl.data.PositionKey
import proj.ankichess.axl.core.impl.data.StoredNode
import proj.ankichess.axl.core.impl.engine.Game
import proj.ankichess.axl.core.intf.data.DatabaseHolder
import proj.ankichess.axl.core.intf.data.getCommonDataBase

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
  fun addChild(move: String, child: Node) {
    linkedMoves.addNextMove(move)
    next = child
  }

  /**
   * Saves this node and its ancestors to the database. Persists the position and moves, then
   * recursively saves the previous node.
   */
  suspend fun save() {
    getCommonDataBase().insertPosition(StoredNode(position, linkedMoves))
    previous?.save()
  }

  /**
   * Deletes this node and its descendants from the database. Recursively deletes child nodes and
   * clears the moves.
   */
  suspend fun delete() {
    NodeFactory.clearNextMoves(position)
    linkedMoves.nextMoves.forEach { move ->
      val game = createGame()
      game.playMove(move)
      val childNode = NodeFactory.createNode(game, this, move)
      childNode.deleteFromPrevious(move)
    }
    DatabaseHolder.getDatabase().deletePosition(position.fenRepresentation)
    linkedMoves.nextMoves.clear()
    next = null
  }

  private suspend fun deleteFromPrevious(previousMove: String) {
    println("Deleting from previous: $previousMove. Position: $position")
    NodeFactory.clearPreviousMove(position, previousMove)
    check(!linkedMoves.previousMoves.contains(previousMove)) { "$previousMove not removed." }
    if (linkedMoves.previousMoves.isNotEmpty()) {
      DatabaseHolder.getDatabase().insertPosition(StoredNode(position, linkedMoves))
    } else {
      delete()
    }
  }
}
