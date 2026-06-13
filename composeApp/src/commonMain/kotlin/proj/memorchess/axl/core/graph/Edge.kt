package proj.memorchess.axl.core.graph

import kotlin.time.Instant
import proj.memorchess.axl.core.data.PositionKey

/**
 * Immutable edge in the [OpeningTree].
 *
 * An edge represents a single move from [from] to [to]. The same logical edge is referenced by both
 * the origin node's outgoing map and the destination node's incoming map; both references hold the
 * same [Edge] instance after any mutation routed through [TreeStore].
 *
 * [updatedAt] is intentionally required from every caller so that two Edges built at different
 * moments do not silently compare unequal because of a hidden clock read. [TreeStore] stamps it
 * when the edge is created or loaded from disk; tests supply a fixed instant.
 *
 * @property from Position the move is played from.
 * @property move Move in standard algebraic notation.
 * @property to Position reached after the move.
 * @property isGood Whether the move is part of the user's intended repertoire. `null` for a move
 *   that was explored but not yet classified. A `false` value flags a move kept around as a known
 *   mistake to drill against.
 * @property createdAt Moment the edge was first added to the repertoire. Unlike [updatedAt], it is
 *   preserved by [TreeStore.addMove] across re-upserts, which makes it a stable sibling order for
 *   the introduction of new cards (see [OpeningTree.introductionOrder]).
 * @property updatedAt Last time the edge was written. Stamped by [TreeStore].
 * @property isDeleted Tombstone flag used by [DeleteMode.SOFT].
 */
data class Edge(
  val from: PositionKey,
  val move: String,
  val to: PositionKey,
  val isGood: Boolean?,
  val createdAt: Instant,
  val updatedAt: Instant,
  val isDeleted: Boolean = false,
)
