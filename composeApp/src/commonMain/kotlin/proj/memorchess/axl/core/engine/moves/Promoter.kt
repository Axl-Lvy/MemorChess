package proj.memorchess.axl.core.engine.moves

import proj.memorchess.axl.core.engine.Game
import proj.memorchess.axl.core.engine.board.IBoard
import proj.memorchess.axl.core.engine.board.Tile
import proj.memorchess.axl.core.engine.pieces.Pawn
import proj.memorchess.axl.core.engine.pieces.PieceFactory

class Promoter(val board: IBoard) {

  /** True to block the game and ask for a promotion. */
  var needPromotion = false

  var newPieceName: String? = null

  private var tileToPromote: Tile? = null

  fun savePromotion(stringMove: String) {
    newPieceName =
      if (stringMove.contains("=")) {
        val secondPart = stringMove.split("=")[1]
        if (secondPart.endsWith("+") || secondPart.endsWith("#")) {
          secondPart.substring(0, secondPart.length - 1)
        } else {
          secondPart
        }
      } else {
        null
      }
  }

  fun update(move: Move) {
    val piece = board.getTile(move.destination()).getSafePiece()
    if (isAtTheEdge(move.destination()) && piece is Pawn) {
      newPieceName =
        if (piece.player == Game.Player.WHITE) {
          newPieceName?.uppercase()
        } else {
          newPieceName?.lowercase()
        }
      tileToPromote = board.getTile(move.destination()) as Tile
      if (newPieceName != null) {
        applyPromotion()
      } else {
        needPromotion = true
      }
    }
  }

  fun applyPromotion() {
    check(!(tileToPromote == null || newPieceName == null)) {
      "Missing information before promoting."
    }
    val oldPiece = tileToPromote!!.piece!!
    board.piecePositionsCache[oldPiece.toString()]?.remove(tileToPromote!!.getCoords())
    board.piecePositionsCache[newPieceName!!]?.add(tileToPromote!!.getCoords())
    tileToPromote!!.piece = PieceFactory.createPiece(newPieceName!!)
    tileToPromote = null
    newPieceName = null
    needPromotion = false
  }

  private fun isAtTheEdge(coords: Pair<Int, Int>): Boolean {
    return coords.first == 0 || coords.first == 7
  }
}
