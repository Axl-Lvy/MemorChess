package proj.memorchess.axl.core.data.explorer

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Response from the Lichess Opening Explorer API.
 *
 * Aggregates white, black and draw counts across all the games stored in Lichess's database that
 * reached the queried position. The [moves] list contains the moves played from that position
 * sorted by total games, descending. [opening] carries the ECO code and human readable name when
 * the position matches a known opening.
 *
 * @property white Total games where white won.
 * @property draws Total games drawn.
 * @property black Total games where black won.
 * @property moves Moves played from this position, sorted by total games descending.
 * @property opening Opening metadata, `null` when the position is not in the openings database.
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
 * @property uci Move in UCI long algebraic notation, for example `e2e4`.
 * @property san Move in standard algebraic notation, for example `e4`. This is the SAN that
 *   [proj.memorchess.axl.core.engine.GameEngine] accepts.
 * @property averageRating Average rating of players that played this move in the sampled games.
 *   Only populated by the lichess source; `null` for masters.
 * @property white Games where white won after this move.
 * @property draws Games drawn after this move.
 * @property black Games where black won after this move.
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

/**
 * Opening metadata returned alongside a Lichess explorer response.
 *
 * @property eco ECO code, for example `C26`.
 * @property name Human readable opening name, for example `Vienna Game: Vienna Gambit`.
 */
@Serializable data class LichessOpening(val eco: String, val name: String)
