package proj.ankichess.axl.core.graph.nodes

import proj.ankichess.axl.core.engine.Game
import proj.ankichess.axl.core.engine.parser.FenParser
import proj.ankichess.axl.core.engine.pieces.Pawn

/** Node factory singleton. */
object NodeFactory {

  /**
   * Cache to prevent creating a node twice.
   *
   * TODO: handle many index
   */
  private val cache = createCache()

  /**
   * Creates a node.
   *
   * @param game The game at the position we want to store.
   * @return The node.
   */
  fun createNode(game: Game, parent: INode, move: String): INode {
    val key = createKey(game)
    return cache.getOrPut(key) { Node(parent, move, key) }
  }

  fun createKey(game: Game): String {
    val fen = FenParser.parse(game).split(" ")
    val keyBuilder = StringBuilder()
    for (i in 0..2) {
      keyBuilder.append(fen[i]).append(" ")
    }
    if (isEnpassantNecessary(game)) {
      keyBuilder.append(fen[2]).append(" ")
    }
    return keyBuilder.toString()
  }

  private fun isEnpassantNecessary(game: Game): Boolean {
    if (game.enPassantColumn == -1) {
      return false
    }

    val checkingRow = if (game.playerTurn == Game.Player.WHITE) 4 else 3
    if (game.enPassantColumn > 0) {
      val piece = game.board.getTile(checkingRow, game.enPassantColumn + 1).getSafePiece()
      if (piece is Pawn && piece.player == game.playerTurn.other()) {
        return true
      }
    }
    if (game.enPassantColumn < 7) {
      val piece = game.board.getTile(checkingRow, game.enPassantColumn - 1).getSafePiece()
      if (piece is Pawn && piece.player == game.playerTurn.other()) {
        return true
      }
    }
    return false
  }

  private fun createCache(): MutableMap<String, INode> {
    val rootKey = createKey(Game())

    return mutableMapOf(rootKey to RootNode(rootKey))
  }
}
