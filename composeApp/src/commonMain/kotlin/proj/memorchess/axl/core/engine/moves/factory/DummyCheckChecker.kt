package proj.memorchess.axl.core.engine.moves.factory

import proj.memorchess.axl.core.engine.Game
import proj.memorchess.axl.core.engine.board.IPosition
import proj.memorchess.axl.core.engine.board.ITile
import proj.memorchess.axl.core.engine.board.Tile
import proj.memorchess.axl.core.engine.moves.IMove
import proj.memorchess.axl.core.engine.moves.description.MoveDescription
import proj.memorchess.axl.core.engine.pieces.IPiece

class DummyCheckChecker(position: IPosition) : ACheckChecker(position) {

  private var changes = mapOf<Pair<Int, Int>, Pair<Int, Int>?>()

  override fun isPossible(move: IMove): Boolean {
    changes = move.generateChanges()
    val player = position.board.getTile(move.origin()).getSafePiece()?.player ?: return false
    val kingPositionAfterMove = findKingPositionAfterMove(move, player)
    IPiece.PIECES.map { if (player == Game.Player.WHITE) it else it.uppercase() }
      .forEach {
        position.board.piecePositionsCache
          .getOrElse(it) { emptySet() }
          .forEach { pos ->
            run {
              val moveDescription = MoveDescription(pos, kingPositionAfterMove)
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

  private fun findKingPositionAfterMove(move: IMove, player: Game.Player): Pair<Int, Int> {
    val kingPositionAfterMove =
      if (
        position.board.getTile(move.origin()).getSafePiece()?.toString()?.lowercase() == IPiece.KING
      ) {
        move.destination()
      } else {
        position.board.piecePositionsCache[
            if (player == Game.Player.WHITE) IPiece.KING.uppercase() else IPiece.KING]
          ?.firstOrNull() ?: throw IllegalStateException("King not found")
      }
    return kingPositionAfterMove
  }

  /** Get tile at specific coordinate, but simulating the move that created [changes]. */
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
