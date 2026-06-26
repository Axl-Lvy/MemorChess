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
) {

  /**
   * Computes this node's [NodeState] given which position we [arrivedFrom].
   *
   * Pure function of this node's [incoming] edges, which a single point lookup fully populates, so
   * it is correct under demand paging without consulting the rest of the graph. An [incoming] map
   * with no edges is the root: [NodeState.FIRST]. Otherwise the incoming classifications are
   * aggregated (a mix of good and bad arrivals is a [NodeState.BAD_STATE]) and combined with the
   * classification of the arriving edge.
   *
   * @param arrivedFrom Origin position the user came from, or `null` when no path is tracked.
   */
  fun computeState(arrivedFrom: PositionKey?): NodeState {
    if (incoming.isEmpty()) return NodeState.FIRST

    var aggregateGood: Boolean? = null
    var arrivalGood: Boolean? = null
    for (edge in incoming.values) {
      when (edge.isGood) {
        true -> {
          if (aggregateGood == false) return NodeState.BAD_STATE
          aggregateGood = true
        }
        false -> {
          if (aggregateGood == true) return NodeState.BAD_STATE
          aggregateGood = false
        }
        null -> Unit
      }
      if (arrivedFrom != null && arrivedFrom == edge.from) {
        arrivalGood = edge.isGood
      }
    }
    return determineState(aggregateGood, arrivalGood)
  }

  private fun determineState(aggregateGood: Boolean?, arrivalGood: Boolean?): NodeState =
    when (aggregateGood) {
      null ->
        when (arrivalGood) {
          null -> NodeState.UNKNOWN
          else -> NodeState.BAD_STATE
        }
      true ->
        when (arrivalGood) {
          null -> NodeState.SAVED_GOOD_BUT_UNKNOWN_MOVE
          true -> NodeState.SAVED_GOOD
          false -> NodeState.BAD_STATE
        }
      false ->
        when (arrivalGood) {
          null -> NodeState.SAVED_BAD_BUT_UNKNOWN_MOVE
          true -> NodeState.BAD_STATE
          false -> NodeState.SAVED_BAD
        }
    }
}
