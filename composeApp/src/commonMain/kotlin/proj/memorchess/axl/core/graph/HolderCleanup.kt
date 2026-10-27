package proj.memorchess.axl.core.graph

import proj.memorchess.axl.core.data.DataMove
import proj.memorchess.axl.core.data.DataNode
import proj.memorchess.axl.core.data.DatabaseQueryManager
import proj.memorchess.axl.core.data.PositionKey
import proj.memorchess.axl.core.data.TaggedEdge

/**
 * Plans what removing holds deletes: every candidate move left with no holder, then, from each
 * position other than the start left with no live incoming move, every outgoing move whatever holds
 * it, recursively, together with the position. Reads storage only and writes nothing.
 */
class HolderCleanup(private val database: DatabaseQueryManager) {

  /**
   * Plans the deletions once [candidates] may have lost holders, reading holders as stored with
   * [overlay] applied on top.
   */
  suspend fun plan(candidates: Collection<TaggedEdge>, overlay: HoldOverlay? = null): CleanupPlan =
    Planning(overlay).run(candidates)

  /** The state of one [plan] call. */
  private inner class Planning(private val overlay: HoldOverlay?) {
    private val nodes = mutableMapOf<PositionKey, DataNode?>()
    private val deleted = linkedMapOf<TaggedEdge, DataMove>()
    private val positions = linkedSetOf<PositionKey>()
    private val remarked = mutableListOf<DataMove>()
    private val orphanCandidates = ArrayDeque<PositionKey>()

    suspend fun run(candidates: Collection<TaggedEdge>): CleanupPlan {
      for (candidate in candidates) consider(candidate)
      while (orphanCandidates.isNotEmpty()) cutOffIfOrphaned(orphanCandidates.removeFirst())
      return CleanupPlan(deleted.values.toList(), positions.toList(), remarked)
    }

    /** Deletes [candidate] when nothing holds it, otherwise re-derives its marking. */
    private suspend fun consider(candidate: TaggedEdge) {
      if (candidate in deleted || overlay?.added?.contains(candidate) == true) return
      val stored = storedLiveMove(candidate) ?: return
      val effective = effectiveMarking(stored.ownIsGood, markings(candidate))
      when (effective) {
        null -> delete(candidate, stored)
        stored.isGood -> Unit
        else -> remarked += stored.copy(isGood = effective)
      }
    }

    /** Deletes [position] and every live move leaving it when no live move reaches it any more. */
    private suspend fun cutOffIfOrphaned(position: PositionKey) {
      if (position == PositionKey.START_POSITION || position in positions) return
      val node = nodeOf(position)?.takeIf { !it.isDeleted } ?: return
      val reached =
        node.previousAndNextMoves.previousMoves.values.any {
          !it.isDeleted && it.edge() !in deleted
        } || overlay?.reaches(position) == true
      if (reached) return
      positions += position
      for (move in node.previousAndNextMoves.nextMoves.values) {
        if (!move.isDeleted && move.edge() !in deleted) delete(move.edge(), move)
      }
    }

    private fun delete(edge: TaggedEdge, move: DataMove) {
      deleted[edge] = move
      orphanCandidates += move.destination
    }

    private suspend fun storedLiveMove(edge: TaggedEdge): DataMove? {
      val origin = nodeOf(edge.origin) ?: return null
      val move = origin.previousAndNextMoves.nextMoves[edge.move] ?: return null
      return move.takeIf { !it.isDeleted && it.destination == edge.destination }
    }

    /** The install markings of [edge]'s live tags, with [overlay]'s dropped tags left out. */
    private suspend fun markings(edge: TaggedEdge): List<Boolean> =
      database
        .getTags(edge.origin, edge.destination)
        .filter { overlay?.drops(it.repertoireId, edge) != true }
        .mapNotNull { it.isGood }

    private suspend fun nodeOf(key: PositionKey): DataNode? =
      nodes.getOrPut(key) { database.getPositionIncludingDeleted(key) }
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
) {

  /** Whether the overlay removes [repertoireId]'s hold on [edge]. */
  fun drops(repertoireId: String, edge: TaggedEdge): Boolean =
    repertoireId == this.repertoireId && edge in dropped

  /** Whether an added move reaches [position]. */
  fun reaches(position: PositionKey): Boolean = added.any { it.destination == position }
}

/**
 * What a cleanup changes: the live [moves] and the [positions] it deletes, in deletion order, and
 * the [remarked] candidates something still holds, carrying their re-derived marking.
 */
data class CleanupPlan(
  val moves: List<DataMove>,
  val positions: List<PositionKey>,
  val remarked: List<DataMove> = emptyList(),
)
