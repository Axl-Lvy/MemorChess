package proj.memorchess.axl.core.data.book

import proj.memorchess.axl.core.data.PositionIdentifier

/**
 * Represents a move within a book.
 *
 * @property origin The origin position of the move.
 * @property destination The destination position of the move.
 * @property move The move in standard notation.
 * @property isGood Whether this is a good move (to learn) or bad move (opponent's mistake).
 */
data class BookMove(
  val origin: PositionIdentifier,
  val destination: PositionIdentifier,
  val move: String,
  val isGood: Boolean,
)
