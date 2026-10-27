package proj.memorchess.axl.core.graph

import kotlin.time.Instant
import proj.memorchess.axl.core.data.PositionKey

/**
 * Immutable move from [from] to [to] in the [OpeningTree]; the origin's outgoing map and the
 * destination's incoming map share the same instance.
 *
 * @property move Move in standard algebraic notation.
 * @property isGood Effective marking; `null` when unclassified, `false` for a known mistake.
 * @property createdAt Preserved across re-upserts, so new card introduction order stays stable.
 * @property ownIsGood The user's own marking, or `null` when the user does not hold the move.
 */
data class Edge(
  val from: PositionKey,
  val move: String,
  val to: PositionKey,
  val isGood: Boolean?,
  val createdAt: Instant,
  val updatedAt: Instant,
  val isDeleted: Boolean = false,
  val originDevice: String = "",
  val deviceSeq: Long = 0L,
  val ownIsGood: Boolean? = null,
)
