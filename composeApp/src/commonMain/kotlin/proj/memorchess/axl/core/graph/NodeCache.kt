package proj.memorchess.axl.core.graph

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import proj.memorchess.axl.core.data.DataMove
import proj.memorchess.axl.core.data.DataNode
import proj.memorchess.axl.core.data.PositionKey

/** One position point lookup, the whole of what [NodeCache] needs from persistence. */
fun interface NodeLoader {
  /** The persisted row for [positionKey], or `null` when the position has none. */
  suspend fun load(positionKey: PositionKey): DataNode?
}

/**
 * Outcome of a [NodeCache.resolve].
 *
 * @property node The resolved node, or `null` when the position could not be resolved.
 * @property hit `true` when the node was returned without waiting on a load. A caller that joined
 *   someone else's in flight load reports `false`, so neighbour prefetch fans out for it too.
 */
data class Resolution(val node: Node?, val hit: Boolean)

private enum class Direction {
  OUTGOING,
  INCOMING,
}

private data class TouchedEdge(val direction: Direction, val move: String)

/**
 * What a mutator changed on a key while a load of that key was in flight.
 *
 * One type covers every mutator, because a key can be invalidated and edited within the same load
 * and neither may be dropped. Marks merge as mutators accumulate: [stale] ors, [replacement] takes
 * the later, [edits] union with the later winning per key, [depth] takes the smaller.
 *
 * @property stale The row this load read is superseded on disk.
 * @property replacement A complete node that supersedes the loaded one.
 * @property edits Per move edits, a `null` value being a removal.
 * @property depth A depth a mutator asked for.
 */
private data class RaceMark(
  var stale: Boolean = false,
  var replacement: Node? = null,
  val edits: MutableMap<TouchedEdge, Edge?> = mutableMapOf(),
  var depth: Int? = null,
)

/**
 * Bounded LRU cache of [Node]s with single flight loading and race reconciliation.
 *
 * Owns the [OpeningTree], the mutex guarding it, the set of in flight load claims, and the marks
 * recording what a mutator changed under a load. Every public method takes the mutex; each is a
 * thin wrapper over a private body that assumes the lock is already held, because finalization
 * marks, clears and inserts in one critical section and [Mutex] is not reentrant.
 *
 * @param loader Point lookup used on a miss.
 * @param loadScope Scope loads run on. The cache derives its own [SupervisorJob] child of it, so
 *   one failed load never cancels a sibling or the caller, while a transient cache still dies with
 *   whatever owns the scope it was built from. Hand a scope whose [Job] nobody joins, such as a
 *   [SupervisorJob] or a lifecycle scope. The derived supervisor is a permanent child of it, so a
 *   caller that joins the handed [Job] would wait forever.
 */
class NodeCache(private val loader: NodeLoader, loadScope: CoroutineScope) {

  private val scope =
    CoroutineScope(loadScope.coroutineContext + SupervisorJob(loadScope.coroutineContext[Job]))

  private val tree = OpeningTree()
  private val mutex = Mutex()
  private val inFlight = mutableMapOf<PositionKey, Deferred<Node?>>()
  private val raced = mutableMapOf<PositionKey, RaceMark>()

  /**
   * Resolves the node at [positionKey], loading it on a miss and deduping concurrent callers onto
   * one load.
   *
   * A mutator's edits always survive a concurrent load, including across the one retry a
   * superseding write forces, and the node returned is always the one left in the cache. After two
   * invalidations inside a single load chain the caller can see a row one sync cycle behind.
   * Nothing corrects that on its own: it survives until eviction, the next invalidation, or the
   * next mutation of the key.
   */
  suspend fun resolve(positionKey: PositionKey): Resolution {
    val claim = mutex.withLock {
      // The claim check comes before the residency check. A key that is resident with a claim in
      // flight is a key whose cached row a mutator or a sync apply has just superseded, and the
      // claim is the read that will replace it.
      val existing = inFlight[positionKey]
      if (existing != null) {
        existing
      } else {
        val resident = tree[positionKey]
        if (resident != null) {
          tree.touch(positionKey)
          return Resolution(resident, hit = true)
        }
        installClaimUnlocked(positionKey, attempt = 1, seed = null)
      }
    }
    return Resolution(claim.await(), hit = false)
  }

  /** The resident node at [positionKey], without loading and without changing recency. */
  suspend fun peek(positionKey: PositionKey): Node? = mutex.withLock { tree[positionKey] }

  /**
   * Drops [positionKey] from the cache and marks any load of it in flight as reading a superseded
   * row. Call it only after the durable write it reflects, or the retry reads the same row twice.
   */
  suspend fun invalidate(positionKey: PositionKey) {
    mutex.withLock {
      markUnlocked(positionKey)?.stale = true
      tree.removeNode(positionKey)
    }
  }

  /** Inserts or replaces [node], marking any load of its key in flight. */
  suspend fun put(node: Node) {
    mutex.withLock {
      markUnlocked(node.positionKey)?.replacement = node
      tree.put(node)
    }
  }

  /** Creates [positionKey] at [depth] if missing, lowering an existing entry's depth. */
  suspend fun ensure(positionKey: PositionKey, depth: Int) {
    mutex.withLock {
      lowerMarkedDepth(positionKey, depth)
      tree.ensure(positionKey, depth)
    }
  }

  /**
   * Unguarded [ensure], safe only before any load on this cache has been triggered. With no load in
   * flight there is nothing to race and nothing to mark.
   */
  fun ensureUnlocked(positionKey: PositionKey, depth: Int) {
    tree.ensure(positionKey, depth)
  }

  /**
   * Adds or replaces [edge], creating either endpoint at [fromDepth] / [fromDepth] + 1 if missing.
   */
  suspend fun upsertEdge(edge: Edge, fromDepth: Int) {
    mutex.withLock {
      // Both endpoints are marked here rather than inside OpeningTree.upsertEdge: a shared helper
      // that marked only the key it was handed would silently drop the destination.
      markEdgeUnlocked(edge.from, Direction.OUTGOING, edge.move, edge, fromDepth)
      markEdgeUnlocked(edge.to, Direction.INCOMING, edge.move, edge, fromDepth + 1)
      tree.upsertEdge(edge, fromDepth)
    }
  }

  /**
   * Removes the edge [from] + [move] from both resident endpoints.
   *
   * @param to Destination of the edge, or `null` when the caller does not know it. A known
   *   destination is marked too, so a load of it in flight cannot resurrect the edge.
   */
  suspend fun removeEdge(from: PositionKey, move: String, to: PositionKey?) {
    mutex.withLock {
      // Marked before the removal, not after: OpeningTree.removeEdge returns early when the origin
      // is not resident, and the mark has to be set even when the removal itself was a no op.
      markEdgeUnlocked(from, Direction.OUTGOING, move, null, null)
      if (to != null) markEdgeUnlocked(to, Direction.INCOMING, move, null, null)
      tree.removeEdge(from, move)
    }
  }

  /** Empties the cache and marks every load in flight as reading a superseded row. */
  suspend fun clear() {
    mutex.withLock {
      for (positionKey in inFlight.keys) raced.getOrPut(positionKey) { RaceMark() }.stale = true
      tree.clear()
    }
  }

  /** Number of entries currently resident. */
  internal suspend fun residentCount(): Int = mutex.withLock { tree.residentCount() }

  /**
   * Installs a claim for [positionKey] and returns it. The caller must hold [mutex].
   *
   * The `async` body reaches its own finalization only by taking [mutex], which this caller still
   * holds, so the claim is always recorded in [inFlight] before finalization can remove it. That
   * holds even on an unconfined dispatcher, where the body starts running inline.
   */
  private fun installClaimUnlocked(
    positionKey: PositionKey,
    attempt: Int,
    seed: RaceMark?,
  ): Deferred<Node?> {
    if (seed != null) raced[positionKey] = seed
    val deferred = scope.async { runLoad(positionKey, attempt) }
    inFlight[positionKey] = deferred
    return deferred
  }

  /**
   * Loads [positionKey], finalizes it into the cache, and drives the single retry a superseded read
   * forces. Runs inside the claim's `async` body so a cancelled caller cannot skip either half.
   */
  private suspend fun runLoad(positionKey: PositionKey, attempt: Int): Node? {
    var loaded: Node? = null
    var loadedOk = false
    var candidate: Node? = null
    var retry: Deferred<Node?>? = null
    try {
      loaded = loader.load(positionKey)?.toNode()
      loadedOk = true
    } finally {
      mutex.withLock {
        // Remove the claim first, then insert, then install the retry. Inserting while the key
        // still held a claim would have finalization mark itself, and installing the retry before
        // the insert would have the insert mark the retry.
        inFlight.remove(positionKey)
        val mark = raced.remove(positionKey)
        if (!loadedOk) {
          // On a second attempt the tree holds the superseded candidate this load was installed to
          // replace, so a failure must take it with it. The database is authoritative for the edits
          // that go too, and the next resolve reads them back.
          if (attempt == 2) tree.removeNode(positionKey)
        } else {
          candidate = reconcile(positionKey, loaded, mark)
          val resolved = candidate
          if (resolved == null) tree.removeNode(positionKey) else tree.put(resolved)
          if (mark != null && mark.stale && attempt == 1) {
            retry = installClaimUnlocked(positionKey, attempt = 2, seed = mark.copy(stale = false))
          }
        }
      }
    }
    val pending = retry
    return if (pending != null) pending.await() else candidate
  }

  /**
   * Folds [mark] into [loaded] to produce the node this load leaves resident, or `null` when the
   * key must be dropped instead.
   */
  private fun reconcile(positionKey: PositionKey, loaded: Node?, mark: RaceMark?): Node? {
    if (mark == null) return loaded
    var base =
      mark.replacement
        ?: loaded
        // Synthesized only on positive evidence the node exists: an edge the mutator added, or a
        // depth it asked for. Removals are not evidence, so a mark holding nothing but removals
        // over an absent row leaves the key dropped instead of resident as an empty shell, which
        // Node.computeState would read as the root.
        ?: if (mark.edits.values.any { it != null } || mark.depth != null) {
          Node(positionKey = positionKey, depth = mark.depth ?: Int.MAX_VALUE)
        } else {
          null
        }
    if (base == null) return null
    if (mark.edits.isNotEmpty()) {
      val outgoing = base.outgoing.toMutableMap()
      val incoming = base.incoming.toMutableMap()
      for ((touched, edge) in mark.edits) {
        val target = if (touched.direction == Direction.OUTGOING) outgoing else incoming
        if (edge == null) target.remove(touched.move) else target[touched.move] = edge
      }
      base = base.copy(outgoing = outgoing, incoming = incoming)
    }
    val markedDepth = mark.depth
    // Never the mark's depth on its own: upsertEdge marks fromDepth and fromDepth + 1, which on a
    // deep exploration line can exceed the shortest path the loaded row already records.
    if (markedDepth != null && markedDepth < base.depth) base = base.copy(depth = markedDepth)
    return base
  }

  /** The mark for [positionKey], creating it, or `null` when no load of that key is in flight. */
  private fun markUnlocked(positionKey: PositionKey): RaceMark? {
    if (positionKey !in inFlight) return null
    return raced.getOrPut(positionKey) { RaceMark() }
  }

  private fun markEdgeUnlocked(
    positionKey: PositionKey,
    direction: Direction,
    move: String,
    edge: Edge?,
    depth: Int?,
  ) {
    val mark = markUnlocked(positionKey) ?: return
    mark.edits[TouchedEdge(direction, move)] = edge
    if (depth != null) mark.depth = minOf(mark.depth ?: depth, depth)
  }

  private fun lowerMarkedDepth(positionKey: PositionKey, depth: Int) {
    val mark = markUnlocked(positionKey) ?: return
    mark.depth = minOf(mark.depth ?: depth, depth)
  }
}

private fun DataMove.toEdge(): Edge =
  Edge(
    from = origin,
    move = move,
    to = destination,
    isGood = isGood,
    createdAt = createdAt,
    updatedAt = updatedAt,
    isDeleted = isDeleted,
    originDevice = originDevice,
    deviceSeq = deviceSeq,
  )

/**
 * Builds a fully edged [Node] from a persisted [DataNode]: non deleted incoming and outgoing moves
 * become [Edge]s. A single point lookup returns both directions, so this rebuilds one node
 * completely.
 */
private fun DataNode.toNode(): Node {
  val outgoing = mutableMapOf<String, Edge>()
  val incoming = mutableMapOf<String, Edge>()
  for (move in previousAndNextMoves.nextMoves.values) {
    if (move.isDeleted) continue
    outgoing[move.move] = move.toEdge()
  }
  for (move in previousAndNextMoves.previousMoves.values) {
    if (move.isDeleted) continue
    incoming[move.move] = move.toEdge()
  }
  return Node(
    positionKey = positionKey,
    outgoing = outgoing,
    incoming = incoming,
    depth = depth,
    cardState = cardState,
  )
}
