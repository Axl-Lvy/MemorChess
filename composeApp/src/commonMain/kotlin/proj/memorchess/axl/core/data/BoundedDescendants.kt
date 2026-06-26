package proj.memorchess.axl.core.data

/**
 * Capped breadth-first descendant count shared by every [DatabaseQueryManager] backend.
 *
 * Counts [root] plus every position a recursive delete from it would remove, bounded by [cap]
 * (returns [cap] once reached, so callers can render "[cap]+"). [liveSingleParentChildren] yields,
 * for a position, the non-deleted child positions whose only incoming parent is within this subtree
 * (the convergence rule: a position reachable through an outside parent survives a delete and is
 * left alone). The walk holds only the visited set and the frontier queue, so it stays bounded
 * regardless of repertoire size and never reads the whole graph.
 *
 * Assumes [root] exists and is live; backends return 0 themselves when it does not.
 */
internal suspend fun cappedDescendantCount(
  root: PositionKey,
  cap: Int,
  liveSingleParentChildren: suspend (PositionKey) -> List<PositionKey>,
): Int {
  if (cap <= 0) return 0
  var count = 1
  val visited = mutableSetOf(root)
  val queue = ArrayDeque<PositionKey>()
  queue.addLast(root)
  while (queue.isNotEmpty() && count < cap) {
    for (child in liveSingleParentChildren(queue.removeFirst())) {
      if (!visited.add(child)) continue
      count++
      if (count >= cap) break
      queue.addLast(child)
    }
  }
  return count
}
