package proj.ankichess.axl.core.intf.engine.board

import proj.ankichess.axl.core.impl.engine.Game

/** A position identifies the state of the board during a game. */
interface IPosition {
  /** The board. */
  val board: IBoard

  /** The player whose turn it is. */
  var playerTurn: Game.Player

  /** The possible castling moves. In this order: KQkq. */
  val possibleCastles: Array<Boolean>

  /** The column for en passant. -1 means no column. */
  var enPassantColumn: Int
}
