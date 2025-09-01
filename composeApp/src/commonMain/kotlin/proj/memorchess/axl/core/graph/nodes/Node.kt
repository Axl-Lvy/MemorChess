package proj.memorchess.axl.core.graph.nodes

import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import proj.memorchess.axl.core.data.DataMove
import proj.memorchess.axl.core.data.DataNode
import proj.memorchess.axl.core.data.DatabaseQueryManager
import proj.memorchess.axl.core.data.PositionIdentifier
import proj.memorchess.axl.core.date.PreviousAndNextDate
import proj.memorchess.axl.core.engine.Game

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
class Node(
  val position: PositionIdentifier,
  val previousAndNextMoves: PreviousAndNextMoves = PreviousAndNextMoves(),
  var previous: Node? = null,
  var next: Node? = null,
) : KoinComponent {

  private val database by inject<DatabaseQueryManager>()

  private val nodeManager by inject<NodeManager>()

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
  fun addChild(move: DataMove, child: Node) {
    previousAndNextMoves.addNextMove(move)
    next = child
  }

  /**
   * Saves this node and its ancestors to the database. Persists the position and moves, then
   * recursively saves the previous node.
   */
  private suspend fun save() {
    DataNode(position, previousAndNextMoves.filterValidMoves(), PreviousAndNextDate.dummyToday())
      .save()
  }

  /** Sets this node as [good][DataMove.isGood] and saves it to the database. */
  suspend fun saveGood() {
    previousAndNextMoves.setPreviousMovesAsGood()
    save()
    previous?.saveBad()
  }

  /** Sets this node as [bad][DataMove.isGood] and saves it to the database. */
  private suspend fun saveBad() {
    previousAndNextMoves.setPreviousMovesAsBadIfNotMarked()
    save()
    previous?.saveGood()
  }

  /**
   * Deletes this node and its descendants from the database. Recursively deletes child nodes and
   * clears the moves.
   */
  suspend fun delete() {
    previousAndNextMoves.nextMoves.values.forEach { move ->
      val game = createGame()
      game.playMove(move.move)
      val childNode = nodeManager.createNode(game, this, move.move)
      childNode.deleteFromPrevious(move)
    }
    nodeManager.clearNextMoves(position)
    database.deletePosition(position)
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

  /**
   * Calculates the number of nodes to delete starting from this node.
   *
   * Previous moves can be null, which means that the node is the first one in the deletion chain
   * (it must be deleted)
   *
   * @param previousMove The previous move to consider for deletion.
   * @return The number of nodes that would be deleted.
   */
  fun calculateNumberOfNodesToDelete(previousMove: DataMove? = null): Int {
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

  /**
   * Calculate the [state][NodeState] of this node.
   *
   * @return The node state.
   */
  fun getState(): NodeState {
    val previousMoves = previousAndNextMoves.previousMoves
    if (previousMoves.isEmpty()) {
      return NodeState.FIRST
    }
    var isGood: Boolean? = null
    var isPreviousMoveGood: Boolean? = null
    val previousNode = previous
    previousMoves.forEach {
      if (it.value.isGood == true) {
        if (isGood == false) {
          return NodeState.BAD_STATE
        }
        isGood = true
      } else if (it.value.isGood == false) {
        if (isGood == true) {
          return NodeState.BAD_STATE
        }
        isGood = false
      }
      if (previousNode != null && previousNode.position == it.value.origin) {
        isPreviousMoveGood = it.value.isGood
      }
    }
    return determineState(isGood, isPreviousMoveGood)
  }

  private fun determineState(isGood: Boolean?, isPreviousMoveGood: Boolean?): NodeState {
    return when (isGood) {
      null ->
        when (isPreviousMoveGood) {
          null -> NodeState.UNKNOWN
          else -> NodeState.BAD_STATE
        }

      true ->
        when (isPreviousMoveGood) {
          null -> NodeState.SAVED_GOOD_BUT_UNKNOWN_MOVE
          true -> NodeState.SAVED_GOOD
          false -> NodeState.BAD_STATE
        }

      false ->
        when (isPreviousMoveGood) {
          null -> NodeState.SAVED_BAD_BUT_UNKNOWN_MOVE
          true -> NodeState.BAD_STATE
          false -> NodeState.SAVED_BAD
        }
    }
  }

  /**
   * A class that represent the state of a node according to the database.
   *
   * @property saved Whether the node has been saved to the database.
   * @property good Whether the node is good.
   * @property previousMoveKnown Whether the previous move is known.
   */
  enum class NodeState(
    private val saved: Boolean,
    private val good: Boolean,
    private val previousMoveKnown: Boolean,
  ) {
    /** This node the first one. */
    FIRST(true, true, true),

    /** Node not stored */
    UNKNOWN(false, false, false),

    /** Node stored as good. Its previous move is also stored */
    SAVED_GOOD(true, true, true),

    /** Node stored as bad. Its previous move is also stored */
    SAVED_BAD(true, false, true),

    /** Node stored as good but from another move */
    SAVED_GOOD_BUT_UNKNOWN_MOVE(true, true, false),

    /** Node stored as bad but from another move */
    SAVED_BAD_BUT_UNKNOWN_MOVE(true, false, false),

    /**
     * Node in a bad state. For example if a bad move and a good move lead to it.
     *
     * It should be removed.
     */
    BAD_STATE(true, false, true),
  }
}
