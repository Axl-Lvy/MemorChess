package proj.memorchess.axl.core.graph

import co.touchlab.kermit.Logger
import proj.memorchess.axl.core.data.DataMove
import proj.memorchess.axl.core.data.DataNode
import proj.memorchess.axl.core.data.DatabaseQueryManager
import proj.memorchess.axl.core.data.PositionKey
import proj.memorchess.axl.core.date.DateUtil
import proj.memorchess.axl.core.scheduling.CardState

/**
 * Single mutation chokepoint for the opening tree.
 *
 * Persistence is authoritative. The in memory [OpeningTree] returned by [current] is a derived
 * cache rebuilt from disk by [load]. Every mutation writes the cache and, when the new state is
 * eligible, the underlying database.
 *
 * Exploration moves that have not yet been classified (`isGood == null`) live in the cache only.
 * They become persisted as soon as a caller upserts them with a non null [Edge.isGood]. This keeps
 * the wasmJs IndexedDB schema, which requires `isGood`, happy without forcing every probe move down
 * the wire.
 *
 * Callers from the UI, interactions and scheduling layers all go through this class. Tests should
 * do the same instead of mutating [DatabaseQueryManager] directly when possible.
 */
class TreeStore(private val database: DatabaseQueryManager) {

  private val tree = OpeningTree()

  /** Returns the cached [OpeningTree]. */
  fun current(): OpeningTree = tree

  /**
   * Rebuilds the cache from persisted state. Wipes the tree first to avoid leaking stale rows that
   * have been hard deleted on disk since the last load.
   */
  suspend fun load() {
    tree.clear()
    val nodes = database.getAllNodes()
    val newPositions = mutableMapOf<PositionKey, Node>()
    for (dataNode in nodes) {
      val key = dataNode.positionKey
      val outgoing = mutableMapOf<String, Edge>()
      val incoming = mutableMapOf<String, Edge>()
      for (move in dataNode.previousAndNextMoves.nextMoves.values) {
        if (move.isDeleted) continue
        outgoing[move.move] = move.toEdge()
      }
      for (move in dataNode.previousAndNextMoves.previousMoves.values) {
        if (move.isDeleted) continue
        incoming[move.move] = move.toEdge()
      }
      newPositions[key] =
        Node(
          positionKey = key,
          outgoing = outgoing,
          incoming = incoming,
          depth = dataNode.depth,
          cardState = dataNode.cardState,
        )
    }
    tree.replaceFrom(newPositions)
    LOGGER.i { "Loaded ${newPositions.size} positions from disk" }
  }

  /**
   * Ensures [positionKey] exists in the cache at the given [depth]. No persistence side effect:
   * exploration of a fresh position should not write a row until the user saves something.
   */
  fun ensurePosition(positionKey: PositionKey, depth: Int) {
    tree.ensure(positionKey, depth)
  }

  /**
   * Adds or replaces an edge in the graph.
   *
   * Always updates the cache. Persists when [isGood] is not `null`: a classified edge becomes
   * durable, an exploration edge does not. The destination node is created on demand at depth
   * [fromDepth] + 1.
   *
   * When the edge already exists its [Edge.createdAt] is preserved. This is load bearing:
   * exploration replays a line by re-upserting every edge on the way, so a fresh stamp on each
   * upsert would reshuffle the introduction order of new cards just by browsing.
   *
   * @param from Position the move is played from.
   * @param move SAN of the move.
   * @param to Position reached by playing [move].
   * @param isGood Classification of the move. `null` means exploration only.
   * @param fromDepth Depth of [from] used when the node has to be inserted.
   * @return The [Edge] now present in the cache.
   */
  suspend fun addMove(
    from: PositionKey,
    move: String,
    to: PositionKey,
    isGood: Boolean?,
    fromDepth: Int,
  ): Edge {
    val createdAt = tree[from]?.outgoing?.get(move)?.createdAt ?: DateUtil.now()
    val edge =
      Edge(
        from = from,
        move = move,
        to = to,
        isGood = isGood,
        createdAt = createdAt,
        updatedAt = DateUtil.now(),
      )
    tree.upsertEdge(edge, fromDepth)
    if (isGood != null) {
      persistNode(from)
      persistNode(to)
    }
    return edge
  }

  /**
   * Adds every move in [moves] to the cache, then persists all touched nodes in one batch.
   *
   * Behaves like calling [addMove] for each element, except that every node touched by a classified
   * move is written to the database exactly once, through a single
   * [DatabaseQueryManager.insertNodes] call. Existing nodes keep their [CardState]; only their edge
   * maps and, when a shorter path is found, their depth are updated.
   *
   * @param moves Insertions to apply, in order.
   */
  suspend fun addMoves(moves: List<MoveInsertion>) {
    if (moves.isEmpty()) return
    val now = DateUtil.now()
    val touched = linkedSetOf<PositionKey>()
    for (insertion in moves) {
      val createdAt = tree[insertion.from]?.outgoing?.get(insertion.move)?.createdAt ?: now
      val edge =
        Edge(
          from = insertion.from,
          move = insertion.move,
          to = insertion.to,
          isGood = insertion.isGood,
          createdAt = createdAt,
          updatedAt = now,
        )
      tree.upsertEdge(edge, insertion.fromDepth)
      if (insertion.isGood != null) {
        touched += insertion.from
        touched += insertion.to
      }
    }
    val nodesToPersist = touched.mapNotNull { tree[it]?.toDataNode() }
    if (nodesToPersist.isNotEmpty()) {
      database.insertNodes(*nodesToPersist.toTypedArray())
    }
  }

  /**
   * Stores [cardState] on the node at [positionKey] and persists it.
   *
   * Logs a warning and skips the write when [positionKey] is not in the cache; that should only
   * happen if the position was deleted between the scheduler reading it and writing the result
   * back.
   */
  suspend fun updateCardState(positionKey: PositionKey, cardState: CardState) {
    val existing = tree[positionKey]
    if (existing == null) {
      LOGGER.w { "Skipping card state update for unknown position $positionKey" }
      return
    }
    tree.put(existing.copy(cardState = cardState))
    persistNode(positionKey)
  }

  /** Deletes the move [move] leaving [from] in both the cache and the underlying database. */
  suspend fun deleteMove(from: PositionKey, move: String, mode: DeleteMode = DeleteMode.HARD) {
    tree.removeEdge(from, move)
    database.deleteMove(from, move, mode)
  }

  /**
   * Deletes the node at [positionKey] and every incident edge, in both the cache and the database.
   */
  suspend fun deleteNode(positionKey: PositionKey, mode: DeleteMode = DeleteMode.HARD) {
    val node = tree[positionKey]
    if (node != null) {
      for (edge in node.outgoing.values.toList()) {
        tree.removeEdge(positionKey, edge.move)
      }
      for (edge in node.incoming.values.toList()) {
        tree.removeEdge(edge.from, edge.move)
      }
      tree.removeNode(positionKey)
    }
    database.deletePosition(positionKey, mode)
  }

  /** Hard wipes every position and move, both in the cache and on disk. */
  suspend fun eraseAll() {
    database.eraseAll()
    tree.clear()
  }

  private suspend fun persistNode(positionKey: PositionKey) {
    val node = tree[positionKey] ?: return
    database.insertNodes(node.toDataNode())
  }
}

/**
 * One move to insert through [TreeStore.addMoves].
 *
 * Mirrors the parameters of [TreeStore.addMove] so a batch element carries exactly the same
 * information as a single insertion.
 *
 * @property from Position the move is played from.
 * @property move SAN of the move.
 * @property to Position reached by playing [move].
 * @property isGood Classification of the move. `null` means exploration only.
 * @property fromDepth Depth of [from] used when the node has to be inserted.
 */
data class MoveInsertion(
  val from: PositionKey,
  val move: String,
  val to: PositionKey,
  val isGood: Boolean?,
  val fromDepth: Int,
)

private fun DataMove.toEdge(): Edge =
  Edge(
    from = origin,
    move = move,
    to = destination,
    isGood = isGood,
    createdAt = createdAt,
    updatedAt = updatedAt,
    isDeleted = isDeleted,
  )

private fun Edge.toDataMove(): DataMove =
  DataMove(
    origin = from,
    destination = to,
    move = move,
    isGood = isGood,
    isDeleted = isDeleted,
    createdAt = createdAt,
    updatedAt = updatedAt,
  )

private fun Node.toDataNode(): DataNode =
  DataNode(
    positionKey = positionKey,
    previousAndNextMoves =
      PreviousAndNextMoves(
        previousMoves =
          incoming.values.filter { it.isGood != null && !it.isDeleted }.map { it.toDataMove() },
        nextMoves =
          outgoing.values.filter { it.isGood != null && !it.isDeleted }.map { it.toDataMove() },
      ),
    cardState = cardState,
    depth = depth,
    updatedAt = DateUtil.now(),
  )

private val LOGGER = Logger.withTag("TreeStore")
