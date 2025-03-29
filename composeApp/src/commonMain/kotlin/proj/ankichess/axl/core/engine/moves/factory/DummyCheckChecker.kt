package proj.ankichess.axl.core.engine.moves.factory

import proj.ankichess.axl.core.engine.Game
import proj.ankichess.axl.core.engine.board.ITile
import proj.ankichess.axl.core.engine.board.Position
import proj.ankichess.axl.core.engine.board.Tile
import proj.ankichess.axl.core.engine.moves.IMove
import proj.ankichess.axl.core.engine.moves.description.MoveDescription
import proj.ankichess.axl.core.engine.pieces.IPiece

class DummyCheckChecker(position: Position) : ACheckChecker(position) {

  private var changes = mapOf<Pair<Int, Int>, Pair<Int, Int>?>()

  override fun isPossible(move: IMove): Boolean {
    changes = move.generateChanges()
    val player = position.board.getTile(move.origin()).getSafePiece()?.player ?: return false
    val kingPosition =
      if (
        position.board.getTile(move.origin()).getSafePiece()?.toString()?.lowercase() == IPiece.KING
      ) {
        move.destination()
      } else {
        position.board.piecePositionsCache[
            if (player == Game.Player.WHITE) IPiece.KING.uppercase() else IPiece.KING]
          ?.firstOrNull()
      }
    if (kingPosition == null) {
      return false
    }
    IPiece.PIECES.map { if (player == Game.Player.WHITE) it else it.uppercase() }
      .forEach {
        position.board.piecePositionsCache
          .getOrElse(it) { emptySet() }
          .forEach { pos ->
            run {
              val moveDescription = MoveDescription(pos, kingPosition)
              if (
                getTileAtCoords(pos).getSafePiece()?.isMovePossible(moveDescription) == true &&
                  createMoveFrom(moveDescription) != null
              ) {
                return false
              }
            }
          }
      }
    return true
  }

  override fun getTileAtCoords(coords: Pair<Int, Int>): ITile {
    if (!changes.containsKey(coords)) {
      return position.board.getTile(coords)
    }
    val tile = Tile(coords.first, coords.second)
    val refCoords = changes[coords]
    if (refCoords != null) {
      tile.piece = position.board.getTile(refCoords).getSafePiece()
    }
    return tile
  }
}
