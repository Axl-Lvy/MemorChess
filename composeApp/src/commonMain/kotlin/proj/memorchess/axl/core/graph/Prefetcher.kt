package proj.memorchess.axl.core.graph

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Fire and forget one ply warm of a resolved node's neighbours, so the next navigation step is a
 * cache hit.
 *
 * Never recurses: a warm resolves through [NodeCache] directly and does not fan out again, so a miss
 * reaches the immediate neighbours and stops, bounded by the branching factor.
 *
 * @param cache Cache the warms resolve through.
 * @param scope Background scope the warms are launched on.
 */
class Prefetcher(private val cache: NodeCache, private val scope: CoroutineScope) {

  /** Launches a warm of every distinct neighbour of [node], excluding [node] itself. */
  fun warmNeighbors(node: Node) {
    val targets =
      (node.outgoing.values.map { it.to } + node.incoming.values.map { it.from })
        .distinct()
        .filter { it != node.positionKey }
    for (key in targets) {
      scope.launch { cache.resolve(key) }
    }
  }
}
