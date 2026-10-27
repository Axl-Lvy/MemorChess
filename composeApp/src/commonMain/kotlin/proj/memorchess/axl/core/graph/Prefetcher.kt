package proj.memorchess.axl.core.graph

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Fire and forget one ply warm of a node's neighbours, so the next navigation step is a cache hit.
 * Never recurses, so a warm stays bounded by the branching factor.
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
