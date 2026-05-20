package proj.memorchess.axl.core.graph

import proj.memorchess.axl.core.data.PositionKey
import proj.memorchess.axl.core.scheduling.CardState
import proj.memorchess.axl.core.scheduling.CardStateFactory

/**
 * Immutable vertex in the [OpeningTree].
 *
 * Two reverse maps keyed by move SAN keep the local neighbourhood readable. The full graph is
 * traversed by looking up the edge's [Edge.to] or [Edge.from] back in [OpeningTree].
 *
 * @property positionKey Position represented by this node.
 * @property outgoing Edges leaving [positionKey], keyed by [Edge.move].
 * @property incoming Edges arriving at [positionKey], keyed by [Edge.move].
 * @property depth Shortest known distance from the root, in plies.
 * @property cardState Spaced repetition state for this position.
 */
data class Node(
  val positionKey: PositionKey,
  val outgoing: Map<String, Edge> = emptyMap(),
  val incoming: Map<String, Edge> = emptyMap(),
  val depth: Int = Int.MAX_VALUE,
  val cardState: CardState = CardStateFactory.new(),
)
