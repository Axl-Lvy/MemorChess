package proj.memorchess.axl.core.data.explorer

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Lichess Opening Explorer game counts for one position.
 *
 * @property white Games won by white.
 * @property black Games won by black.
 * @property moves Moves played from this position, sorted by total games descending.
 * @property opening `null` when the position is not a known opening.
 */
@Serializable
data class LichessExplorerResponse(
  val white: Long,
  val draws: Long,
  val black: Long,
  val moves: List<LichessExplorerMove>,
  val opening: LichessOpening? = null,
) {
  /** Total number of games stored for this position. */
  val totalGames: Long
    get() = white + draws + black
}

/**
 * A single move row in a [LichessExplorerResponse].
 *
 * @property averageRating Average rating of the players of this move; `null` for masters.
 * @property white Games won by white after this move.
 * @property black Games won by black after this move.
 */
@Serializable
data class LichessExplorerMove(
  val uci: String,
  val san: String,
  @SerialName("averageRating") val averageRating: Int? = null,
  val white: Long,
  val draws: Long,
  val black: Long,
) {
  /** Total number of games stored where this move was played. */
  val totalGames: Long
    get() = white + draws + black
}

/** ECO code and human readable name of an opening. */
@Serializable data class LichessOpening(val eco: String, val name: String)
