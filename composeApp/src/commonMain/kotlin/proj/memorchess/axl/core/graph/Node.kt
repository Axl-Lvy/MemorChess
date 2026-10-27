package proj.memorchess.axl.core.graph

import proj.memorchess.axl.core.data.PositionKey
import proj.memorchess.axl.core.scheduling.CardState
import proj.memorchess.axl.core.scheduling.CardStateFactory

/**
 * Immutable vertex in the [OpeningTree], with its edges keyed by [Edge.move].
 *
 * @property depth Shortest known distance from the root, in plies. No default: a wrong value
 *   silently reorders the new card queue instead of failing loudly.
 */
data class Node(
  val positionKey: PositionKey,
  val outgoing: Map<String, Edge> = emptyMap(),
  val incoming: Map<String, Edge> = emptyMap(),
  val depth: Int,
  val cardState: CardState = CardStateFactory.new(),
) {

  /**
   * Computes this node's [NodeState] when reached from [arrivedFrom] (`null` when no path is
   * tracked). Reads only [incoming], so it stays correct under demand paging.
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
