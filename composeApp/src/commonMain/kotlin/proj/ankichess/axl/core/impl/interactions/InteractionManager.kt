package proj.ankichess.axl.core.impl.interactions

import proj.ankichess.axl.core.impl.engine.Game
import proj.ankichess.axl.core.impl.engine.moves.IllegalMoveException
import proj.ankichess.axl.core.impl.engine.moves.description.MoveDescription
import proj.ankichess.axl.core.impl.graph.nodes.NodeFactory
import proj.ankichess.axl.core.intf.graph.INode
import proj.ankichess.axl.ui.popup.info
import proj.ankichess.axl.ui.util.intf.IReloader

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

  private var node: INode

  init {
    val rootNode = NodeFactory.getNode(game)
    checkNotNull(rootNode)
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
        node = NodeFactory.createNode(game, node, move)
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
    val rootNode = NodeFactory.getNode(game)
    checkNotNull(rootNode)
    node = rootNode
    reloader.reload()
  }

  fun back(reloader: IReloader) {
    val parent = node.getParent()
    if (parent != null) {
      node = parent
      game = node.getGame()
      reloader.reload()
    } else {
      displayMessage("No previous move.")
    }
  }

  fun forward(reloader: IReloader) {
    val firstChild = node.getFirstChild()
    if (firstChild != null) {
      node = firstChild
      game = firstChild.getGame()
      reloader.reload()
    } else {
      displayMessage("No next move.")
    }
  }

  fun playMove(move: String, reloader: IReloader) {
    game.playMove(move)
    node = NodeFactory.createNode(game, node, move)
    reloader.reload()
  }

  fun getChildrenMoves(): List<String> {
    return node.getChildren().keys.toList()
  }

  private fun displayMessage(message: String) {
    info(message)
  }
}
