package proj.memorchess.axl.core.engine.board

import proj.memorchess.axl.core.data.PositionKey
import proj.memorchess.axl.core.engine.Game

/** A position identifies the state of the board during a game. It can change its state. */
interface IPosition {
  /** The board. */
  val board: IBoard

  /** The player whose turn it is. */
  var playerTurn: Game.Player

  /** The possible castling moves. In this order: KQkq. */
  val possibleCastles: Array<Boolean>

  /** The column for en passant. -1 means no column. */
  var enPassantColumn: Int

  /** Returns an immutable [PositionKey] representing this position. */
  fun toImmutablePosition(): PositionKey
}
