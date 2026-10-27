package proj.memorchess.axl.core.graph

import proj.memorchess.axl.core.data.DataMove
import proj.memorchess.axl.core.data.DataNode
import proj.memorchess.axl.core.data.DatabaseQueryManager
import proj.memorchess.axl.core.data.PositionKey
import proj.memorchess.axl.core.data.TaggedEdge

/**
 * Plans what removing holds deletes: every candidate move left with no holder, then, from each
 * position other than the start left with no live incoming move, every outgoing move whatever
 * holds it, recursively, together with the position. Reads storage only and writes nothing.
 *
 * @param database The persistence backend the holders and moves are read from.
 */
class HolderCleanup(private val database: DatabaseQueryManager) {

  /**
   * Plans the deletions once [candidates] may have lost holders, reading holders as stored with
   * [overlay] applied on top.
   */
  suspend fun plan(candidates: Collection<TaggedEdge>, overlay: HoldOverlay? = null): CleanupPlan {
    val nodes = mutableMapOf<PositionKey, DataNode?>()
    suspend fun nodeOf(key: PositionKey): DataNode? =
      nodes.getOrPut(key) { database.getPositionIncludingDeleted(key) }

    val deleted = linkedMapOf<TaggedEdge, DataMove>()
    val positions = linkedSetOf<PositionKey>()
    val orphanCandidates = ArrayDeque<PositionKey>()
    for (candidate in candidates) {
      if (candidate in deleted || overlay?.added?.contains(candidate) == true) continue
      val stored =
        nodeOf(candidate.origin)?.previousAndNextMoves?.nextMoves?.get(candidate.move)?.takeIf {
          !it.isDeleted && it.destination == candidate.destination
        } ?: continue
      if (stored.ownIsGood != null || isTagHeld(candidate, overlay)) continue
      deleted[candidate] = stored
      orphanCandidates += candidate.destination
    }
    while (orphanCandidates.isNotEmpty()) {
      val position = orphanCandidates.removeFirst()
      if (position == PositionKey.START_POSITION || position in positions) continue
      val node = nodeOf(position)?.takeIf { !it.isDeleted } ?: continue
      val reached =
        node.previousAndNextMoves.previousMoves.values.any {
          !it.isDeleted && it.edge() !in deleted
        } || overlay?.added?.any { it.destination == position } == true
      if (reached) continue
      positions += position
      for (move in node.previousAndNextMoves.nextMoves.values) {
        if (move.isDeleted || move.edge() in deleted) continue
        deleted[move.edge()] = move
        orphanCandidates += move.destination
      }
    }
    return CleanupPlan(deleted.values.toList(), positions.toList())
  }

  private suspend fun isTagHeld(edge: TaggedEdge, overlay: HoldOverlay?): Boolean =
    database.getTags(edge.origin, edge.destination).any { tag ->
      tag.isGood != null &&
        !(overlay != null && tag.repertoireId == overlay.repertoireId && edge in overlay.dropped)
    }

  private fun DataMove.edge() = TaggedEdge(origin, destination, move)
}

/**
 * Holds of [repertoireId] simulated over the stored ones: dead on [dropped], live on [added], whose
 * moves also count as live incoming moves of their destinations.
 */
data class HoldOverlay(
  val repertoireId: String,
  val dropped: Set<TaggedEdge>,
  val added: Set<TaggedEdge>,
)

/** Live moves and positions a cleanup deletes, in deletion order. */
data class CleanupPlan(val moves: List<DataMove>, val positions: List<PositionKey>)
