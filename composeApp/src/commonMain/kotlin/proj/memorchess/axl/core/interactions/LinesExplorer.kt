package proj.memorchess.axl.core.interactions

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import proj.memorchess.axl.core.engine.Game
import proj.memorchess.axl.core.graph.nodes.Node
import proj.memorchess.axl.core.graph.nodes.NodeManager
import proj.memorchess.axl.core.util.Reloader
import proj.memorchess.axl.ui.components.popup.info

/** LinesExplorer is an interaction manager that allows exploring the stored lines. */
class LinesExplorer() : InteractionsManager(Game()) {

  /** The current node in the exploration tree. */
  private var node: Node

  init {
    node = NodeManager.createRootNode()
  }

  var state by mutableStateOf(node.getState())

  /**
   * Moves back in the exploration tree to the previous node.
   *
   * @param reloader The reloader to refresh the UI after moving back.
   */
  fun back(reloader: Reloader) {
    val parent = node.previous
    if (parent != null) {
      node = parent
      game = node.createGame()
      state = node.getState()
      reloader.reload()
    } else {
      info("No previous move.")
    }
  }

  /**
   * Moves forward in the exploration tree to the next child node.
   *
   * @param reloader The reloader to refresh the UI after moving forward.
   */
  fun forward(reloader: Reloader) {
    val firstChild = node.next
    if (firstChild != null) {
      val move =
        node.previousAndNextMoves.nextMoves.values.find { it.destination == firstChild.position }
      checkNotNull(move) { "No move found to go to ${firstChild.position}" }
      node = firstChild
      game.playMove(move.move)
      state = node.getState()
      reloader.reload()
    } else {
      info("No next move.")
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

  /**
   * Resets the LinesExplorer to the root node.
   *
   * @param reloader The reloader.
   */
  fun reset(reloader: Reloader) {
    node = NodeManager.createRootNode()
    state = node.getState()
    super.reset(reloader, node.position)
  }

  override suspend fun afterPlayMove(move: String, reloader: Reloader) {
    node = NodeManager.createNode(game, node, move)
    state = node.getState()
    reloader.reload()
  }

  /** Saves the current node as coming from a good move. */
  suspend fun save() {
    node.saveGood()
    state = node.getState()
    info("Saved")
  }

  /**
   * Deletes the current node and reloads the explorer.
   *
   * @param reloader The reloader to refresh the UI after deletion.
   */
  suspend fun delete(reloader: Reloader) {
    node.delete()
    state = node.getState()
    info("Deleted")
    reloader.reload()
  }
}
