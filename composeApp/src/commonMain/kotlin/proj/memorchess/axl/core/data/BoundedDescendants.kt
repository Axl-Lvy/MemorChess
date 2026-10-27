package proj.memorchess.axl.core.data

/**
 * Counts [root] plus every position a recursive delete from it would remove, returning [cap] once
 * reached. Assumes [root] is live; [liveSingleParentChildren] must skip children with an outside
 * parent.
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
