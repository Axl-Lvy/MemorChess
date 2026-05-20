package proj.memorchess.axl.core.graph

import kotlin.time.Instant
import proj.memorchess.axl.core.data.PositionKey
import proj.memorchess.axl.core.date.DateUtil

/**
 * Immutable edge in the [OpeningTree].
 *
 * An edge represents a single move from [from] to [to]. The same logical edge is referenced by both
 * the origin node's outgoing map and the destination node's incoming map; both references hold the
 * same [Edge] instance after any mutation routed through [TreeStore].
 *
 * @property from Position the move is played from.
 * @property move Move in standard algebraic notation.
 * @property to Position reached after the move.
 * @property isGood Whether the move is part of the user's intended repertoire. `null` for a move
 *   that was explored but not yet classified. A `false` value flags a move kept around as a known
 *   mistake to drill against.
 * @property updatedAt Last time the edge was written. Stamped by [TreeStore].
 * @property isDeleted Tombstone flag used by [DeleteMode.SOFT].
 */
data class Edge(
  val from: PositionKey,
  val move: String,
  val to: PositionKey,
  val isGood: Boolean? = null,
  val updatedAt: Instant = DateUtil.now(),
  val isDeleted: Boolean = false,
)
