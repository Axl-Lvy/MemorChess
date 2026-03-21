package proj.memorchess.shared.dto

import kotlinx.serialization.Serializable

/**
 * Data transfer object for a book move.
 *
 * @property origin The FEN representation of the origin position.
 * @property destination The FEN representation of the destination position.
 * @property move The move in standard notation.
 * @property isGood Whether this is a good move (to learn) or a bad move (opponent's mistake).
 */
@Serializable
data class BookMoveDto(
  val origin: String,
  val destination: String,
  val move: String,
  val isGood: Boolean,
)
