package proj.ankichess.axl.core.interactions

import proj.ankichess.axl.core.engine.Game
import proj.ankichess.axl.core.engine.moves.IllegalMoveException
import proj.ankichess.axl.core.engine.moves.description.MoveDescription
import proj.ankichess.axl.core.graph.nodes.INode
import proj.ankichess.axl.core.graph.nodes.NodeFactory
import proj.ankichess.axl.ui.popup.info

/**
 * Class that handles clicks on the chess board.
 *
 * @property game The game.
 * @constructor Creates an interaction manager from a game.
 */
class InteractionManager(val game: Game) {

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

  private fun displayMessage(message: String) {
    info(message)
  }
}
