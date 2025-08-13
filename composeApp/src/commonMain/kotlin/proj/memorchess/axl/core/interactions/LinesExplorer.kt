package proj.memorchess.axl.core.interactions

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import proj.memorchess.axl.core.data.PositionIdentifier
import proj.memorchess.axl.core.engine.Game
import proj.memorchess.axl.core.graph.nodes.Node

/** LinesExplorer is an interaction manager that allows exploring the stored lines. */
class LinesExplorer(position: PositionIdentifier? = null) : InteractionsManager(Game()) {

  /** The current node in the exploration tree. */
  private var node: Node

  init {
    node = nodeManager.createRootNode()
  }

  var state by mutableStateOf(node.getState())

  /** Moves back in the exploration tree to the previous node. */
  fun back() {
    val parent = node.previous
    if (parent != null) {
      node = parent
      game = node.createGame()
      state = node.getState()
      callCallBacks()
    } else {
      toastRenderer.info("No previous move.")
    }
  }

  /** Moves forward in the exploration tree to the next child node. */
  fun forward() {
    val firstChild = node.next
    if (firstChild != null) {
      print("forward called on node: ${node.position}")
      val move =
        node.previousAndNextMoves.nextMoves.values.find { it.destination == firstChild.position }
      checkNotNull(move) { "No move found to go to ${firstChild.position}" }
      node = firstChild
      game.playMove(move.move)
      state = node.getState()
      callCallBacks()
    } else {
      toastRenderer.info("No next move.")
    }
  }

  /**
   * Get the list of moves that can be played from the current position.
   *
   * @return A list of moves that can be played from the current position.
   */
  fun getNextMoves(): List<String> {
    return node.previousAndNextMoves.nextMoves.keys.sorted()
  }

  /** Resets the LinesExplorer to the root node. */
  fun reset() {
    node = nodeManager.createRootNode()
    state = node.getState()
    super.reset(node.position)
  }

  override suspend fun afterPlayMove(move: String) {
    node = nodeManager.createNode(game, node, move)
    state = node.getState()
    callCallBacks()
  }

  /** Saves the current node as coming from a good move. */
  suspend fun save() {
    node.saveGood()
    state = node.getState()
    toastRenderer.info("Saved")
  }

  /** Deletes the current node and reloads the explorer. */
  suspend fun delete() {
    node.delete()
    state = node.getState()
    toastRenderer.info("Deleted")
    callCallBacks()
  }

  /**
   * Calculates the number of nodes that would be deleted if the current node is deleted.
   *
   * @return The number of nodes that would be deleted.
   */
  fun calculateNumberOfNodeToDelete(): Int {
    return node.calculateNumberOfNodesToDelete()
  }
}
