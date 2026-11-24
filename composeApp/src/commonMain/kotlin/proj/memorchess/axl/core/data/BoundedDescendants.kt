package proj.memorchess.axl.core.data

/**
 * Capped breadth-first descendant walk shared by every [DatabaseQueryManager] backend.
 *
 * Returns [root] plus every position a recursive delete from it would remove, bounded by [cap]
 * (holds exactly [cap] positions once reached, so callers can render "[cap]+").
 * [liveSingleParentChildren] yields, for a position, the non-deleted child positions whose only
 * incoming parent is within this subtree (the convergence rule: a position reachable through an
 * outside parent survives a delete and is left alone). The walk holds only the visited set and the
 * frontier queue, so it stays bounded regardless of repertoire size and never reads the whole
 * graph.
 *
 * Assumes [root] exists and is live; backends return an empty impact themselves when it does not.
 */
internal suspend fun cappedDescendants(
  root: PositionKey,
  cap: Int,
  liveSingleParentChildren: suspend (PositionKey) -> List<PositionKey>,
): Set<PositionKey> {
  if (cap <= 0) return emptySet()
  val visited = mutableSetOf(root)
  val queue = ArrayDeque<PositionKey>()
  queue.addLast(root)
  while (queue.isNotEmpty() && visited.size < cap) {
    for (child in liveSingleParentChildren(queue.removeFirst())) {
      if (!visited.add(child)) continue
      if (visited.size >= cap) break
      queue.addLast(child)
    }
  }
  return visited
}
