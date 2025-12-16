package proj.memorchess.axl.core.graph.nodes

import org.koin.core.component.inject
import proj.memorchess.axl.core.data.DataNode
import proj.memorchess.axl.core.data.DatabaseQueryManager
import proj.memorchess.axl.core.data.PositionIdentifier
import proj.memorchess.axl.core.date.PreviousAndNextDate

/**
 * Represents a node in the chess position graph. Each node corresponds to a unique chess position
 * and tracks possible moves, previous moves, and links to previous and next nodes in the graph.
 *
 * @property position The unique key representing the chess position (FEN).
 * @property previousAndNextMoves Holds the moves available from this position and the previous
 *   moves leading to it.
 * @property previous Reference to the previous node in the graph.
 * @property next Reference to the next node in the graph.
 */
class PersonalNode(
  position: PositionIdentifier,
  previousAndNextMoves: PreviousAndNextMoves = PreviousAndNextMoves(),
  previous: PersonalNode? = null,
  next: PersonalNode? = null,
) : Node<PersonalNode>(position, previousAndNextMoves, previous, next) {

  private val database by inject<DatabaseQueryManager>()

  override val nodeManager by inject<NodeManager<PersonalNode>>()

  /**
   * Saves this node and its ancestors to the database. Persists the position and moves, then
   * recursively saves the previous node.
   */
  override suspend fun save() {
    DataNode(position, previousAndNextMoves.filterValidMoves(), PreviousAndNextDate.dummyToday())
      .save()
  }

  override suspend fun delete() {
    previousAndNextMoves.nextMoves.values.forEach { move ->
      val game = createGame()
      game.playMove(move.move)
      val childNode = nodeManager.createNode(game, this, move.move)
      childNode.deleteFromPrevious(move)
    }
    nodeManager.clearNextMoves(position)
    previousAndNextMoves.nextMoves.clear()
    database.deletePosition(position)
    next = null
  }
}
