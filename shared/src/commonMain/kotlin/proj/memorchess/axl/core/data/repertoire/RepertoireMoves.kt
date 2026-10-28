package proj.memorchess.axl.core.data.repertoire

import kotlinx.serialization.Serializable
import proj.memorchess.axl.core.engine.GameEngine

/**
 * One move of a published repertoire.
 *
 * @property from `PositionKey` value of the position the move is played from.
 * @property move The move in canonical standard algebraic notation, without check markers.
 * @property to `PositionKey` value of the position the move leads to.
 * @property isGood The author's marking: `true` for a move to drill, `false` for an opponent reply.
 */
@Serializable
data class RepertoireMove(val from: String, val move: String, val to: String, val isGood: Boolean)

/** Body of `GET /v1/repertoires/{id}/versions/{version}/moves`. */
@Serializable data class RepertoireMoveList(val moves: List<RepertoireMove>)

/**
 * Body of `POST /v1/repertoires`.
 *
 * @property id The catalog slug to publish or republish under.
 * @property side The side the repertoire is built for.
 * @property moves Every move of the repertoire, connected to the starting position.
 */
@Serializable
data class PublishRepertoireRequest(
  val id: String,
  val title: String,
  val description: String,
  val side: RepertoireColor,
  val moves: List<RepertoireMove>,
)

/**
 * Shortest ply distance from the starting position to every position these moves reach, the start
 * itself at `0`. Positions no chain of these moves connects to the start are absent.
 */
fun List<RepertoireMove>.depthsFromStart(): Map<String, Int> {
  if (isEmpty()) return emptyMap()
  val outgoing = groupBy { it.from }
  val start = GameEngine().toPositionKey().value
  val depths = mutableMapOf(start to 0)
  val pending = ArrayDeque(listOf(start))
  while (pending.isNotEmpty()) {
    val position = pending.removeFirst()
    val next = depths.getValue(position) + 1
    for (move in outgoing[position].orEmpty()) {
      if (move.to !in depths) {
        depths[move.to] = next
        pending += move.to
      }
    }
  }
  return depths
}
