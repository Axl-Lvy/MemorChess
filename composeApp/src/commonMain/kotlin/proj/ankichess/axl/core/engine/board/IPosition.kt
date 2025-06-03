package proj.ankichess.axl.core.engine.board

import proj.ankichess.axl.core.data.PositionKey
import proj.ankichess.axl.core.engine.Game

/** A position identifies the state of the board during a game. It can change it state. */
interface IPosition {
  /** The board. */
  val board: IBoard

  /** The player whose turn it is. */
  var playerTurn: Game.Player

  /** The possible castling moves. In this order: KQkq. */
  val possibleCastles: Array<Boolean>

  /** The column for en passant. -1 means no column. */
  var enPassantColumn: Int

  fun toImmutablePosition(): PositionKey
}
