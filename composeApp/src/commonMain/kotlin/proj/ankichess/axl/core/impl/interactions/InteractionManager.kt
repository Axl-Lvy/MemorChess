package proj.ankichess.axl.core.impl.interactions

import proj.ankichess.axl.core.impl.engine.Game
import proj.ankichess.axl.core.impl.engine.moves.IllegalMoveException
import proj.ankichess.axl.core.impl.engine.moves.description.MoveDescription
import proj.ankichess.axl.core.impl.graph.nodes.NodeFactory
import proj.ankichess.axl.core.intf.graph.INode
import proj.ankichess.axl.ui.popup.info

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
    } else if (game.position.board.getTile(coordinates).getSafePiece() != null) {
      firstTile = coordinates
    }
  }

  fun reset() {
    firstTile = null
    game = Game()
    val rootNode = NodeFactory.getNode(game)
    checkNotNull(rootNode)
    node = rootNode
  }

  private fun displayMessage(message: String) {
    info(message)
  }
}
