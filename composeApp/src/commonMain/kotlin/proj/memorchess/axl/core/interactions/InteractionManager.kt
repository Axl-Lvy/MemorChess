package proj.memorchess.axl.core.interactions

import proj.memorchess.axl.core.engine.Game
import proj.memorchess.axl.core.engine.moves.IllegalMoveException
import proj.memorchess.axl.core.engine.moves.description.MoveDescription
import proj.memorchess.axl.core.graph.nodes.Node
import proj.memorchess.axl.core.graph.nodes.NodeManager
import proj.memorchess.axl.core.util.IReloader
import proj.memorchess.axl.ui.components.popup.info

/**
 * Class that handles clicks on the chess board.
 *
 * @property game The game.
 * @constructor Creates an interaction manager from a game.
 */
class InteractionManager(var game: Game) {

  /** Creates an interaction manager from a new game. */
  constructor() : this(game = Game()) {}

  /** Coordinates of the tile that was clicked first. */
  private var firstTile: Pair<Int, Int>? = null

  private var node: Node

  init {
    val rootNode = NodeManager.createRootNode()
    node = rootNode
  }

  /**
   * Clicks on a tile.
   *
   * @param coordinates The clicked tile's coordinates.
   */
  fun clickOnTile(coordinates: Pair<Int, Int>) {
    val immutableFirstTile = firstTile
    if (immutableFirstTile != null) {
      try {
        val move = game.playMove(MoveDescription(immutableFirstTile, coordinates))
        node = NodeManager.createNode(game, node, move)
      } catch (e: IllegalMoveException) {
        displayMessage(e.message.toString())
      }
      firstTile = null
    } else if (
      game.position.board.getTile(coordinates).getSafePiece()?.player == game.position.playerTurn
    ) {
      firstTile = coordinates
    }
  }

  fun reset(reloader: IReloader) {
    firstTile = null
    game = Game()
    node = NodeManager.createRootNode()
    reloader.reload()
  }

  fun back(reloader: IReloader) {
    val parent = node.previous
    if (parent != null) {
      node = parent
      game = node.createGame()
      reloader.reload()
    } else {
      displayMessage("No previous move.")
    }
  }

  fun forward(reloader: IReloader) {
    val firstChild = node.next
    if (firstChild != null) {
      node = firstChild
      game = firstChild.createGame()
      reloader.reload()
    } else {
      displayMessage("No next move.")
    }
  }

  fun playMove(move: String, reloader: IReloader) {
    game.playMove(move)
    node = NodeManager.createNode(game, node, move)
    reloader.reload()
  }

  fun getChildrenMoves(): List<String> {
    return node.linkedMoves.nextMoves.map { it.move }.sorted()
  }

  suspend fun saveGood() {
    node.saveGood()
  }

  suspend fun saveBad() {
    node.saveBad()
  }

  suspend fun delete(reloader: IReloader) {
    node.delete()
    reloader.reload()
  }

  private fun displayMessage(message: String) {
    info(message)
  }
}
