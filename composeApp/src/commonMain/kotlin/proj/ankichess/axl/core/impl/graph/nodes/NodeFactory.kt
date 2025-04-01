package proj.ankichess.axl.core.impl.graph.nodes

import proj.ankichess.axl.core.impl.engine.Game
import proj.ankichess.axl.core.impl.engine.parser.FenParser
import proj.ankichess.axl.core.impl.engine.pieces.Pawn
import proj.ankichess.axl.core.intf.graph.INode

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
    return Node(parent, move, key)
  }

  fun getOrCreateNode(game: Game, parent: INode, move: String): INode {
    val key = createKey(game)
    return cache.getOrPut(key) { Node(parent, move, key) }
  }

  fun getNode(game: Game): INode? {
    val key = createKey(game)
    return cache[key]
  }

  fun cacheNode(node: INode): INode? {
    return cache.put(node.toString(), node)
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
    if (game.position.enPassantColumn == -1) {
      return false
    }

    val checkingRow = if (game.position.playerTurn == Game.Player.WHITE) 4 else 3
    if (game.position.enPassantColumn > 0) {
      val piece =
        game.position.board.getTile(checkingRow, game.position.enPassantColumn - 1).getSafePiece()
      if (piece is Pawn && piece.player == game.position.playerTurn.other()) {
        return true
      }
    }
    if (game.position.enPassantColumn < 7) {
      val piece =
        game.position.board.getTile(checkingRow, game.position.enPassantColumn + 1).getSafePiece()
      if (piece is Pawn && piece.player == game.position.playerTurn.other()) {
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
