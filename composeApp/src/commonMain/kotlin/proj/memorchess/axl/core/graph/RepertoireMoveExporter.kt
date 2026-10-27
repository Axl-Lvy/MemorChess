package proj.memorchess.axl.core.graph

import proj.memorchess.axl.core.data.PositionKey
import proj.memorchess.axl.core.data.repertoire.RepertoireMove
import proj.memorchess.axl.core.data.repertoire.depthsFromStart
import proj.memorchess.axl.core.engine.GameEngine

/** Outcome of [RepertoireMoveExporter.export]. */
sealed class RepertoireExportResult {

  /** The repertoire has no tagged edge at all. */
  data object Empty : RepertoireExportResult()

  /**
   * The exported moves.
   *
   * @property moves Every distinct exported move, with its marking.
   * @property maxPlyDepth Deepest move origin, in plies along the shortest path from the start, the
   *   unit `RepertoirePublishLimits.MAX_PLY_DEPTH` caps.
   */
  data class Moves(val moves: List<RepertoireMove>, val maxPlyDepth: Int) : RepertoireExportResult()
}

/**
 * Turns a repertoire's tagged edges into the move list it is published as, the reverse of
 * [RepertoireMoveImporter].
 *
 * A line that transposes into already known theory can have one or more untagged edges between the
 * starting position and a tagged one further down (`LinesExplorer` only tags an edge the first time
 * it is newly played). Exporting tagged edges alone would leave such a tagged edge disconnected
 * from the start. This keeps an edge when it is itself tagged with the repertoire or leads to a
 * position some live tagged edge is reachable from, so every exported line stays whole and
 * playable. Those positions are found by walking back from the tagged edges, so only the
 * repertoire's own lines and their ancestors are loaded, never the rest of the graph. An untagged
 * move back into a position already on the current line is dropped, and a move only explored, never
 * saved, is never exported. Each move is exported in canonical SAN, so a legacy over-disambiguated
 * spelling still publishes.
 */
class RepertoireMoveExporter(
  private val treeStore: TreeStore,
  private val tagStore: RepertoireTagStore,
) {

  /** Exports every line reachable from a tagged edge of [repertoireId]. See the class doc. */
  suspend fun export(repertoireId: String): RepertoireExportResult {
    val taggedEdges =
      tagStore.edgesTaggedWith(repertoireId).map { it.origin to it.destination }.toSet()
    if (taggedEdges.isEmpty()) return RepertoireExportResult.Empty

    val leadingToTagged = nodesLeadingTo(taggedEdges)
    val line = mutableSetOf<PositionKey>()
    val moves = linkedMapOf<Pair<PositionKey, PositionKey>, RepertoireMove>()

    fun collect(position: PositionKey) {
      val node = leadingToTagged[position] ?: return
      line += position
      val included =
        node.outgoing.values.filter { edge ->
          edge.isLive() &&
            ((edge.from to edge.to) in taggedEdges ||
              (edge.to in leadingToTagged && edge.to !in line))
        }
      for (edge in included) {
        val isNew = (edge.from to edge.to) !in moves
        moves[edge.from to edge.to] =
          RepertoireMove(edge.from.value, canonical(edge), edge.to.value, edge.isGood == true)
        if (isNew) collect(edge.to)
      }
      line -= position
    }

    collect(GameEngine().toPositionKey())
    if (moves.isEmpty()) return RepertoireExportResult.Empty
    val exported = moves.values.toList()
    val depths = exported.depthsFromStart()
    return RepertoireExportResult.Moves(exported, exported.maxOf { depths.getValue(it.from) })
  }

  /** Every position some live edge of [taggedEdges] is reachable from, mapped to its node. */
  private suspend fun nodesLeadingTo(
    taggedEdges: Set<Pair<PositionKey, PositionKey>>
  ): Map<PositionKey, Node> {
    val nodes = mutableMapOf<PositionKey, Node>()
    val pending = ArrayDeque<Node>()
    for (origin in taggedEdges.mapTo(mutableSetOf()) { it.first }) {
      val node = treeStore.node(origin) ?: continue
      if (node.outgoing.values.any { it.isLive() && (it.from to it.to) in taggedEdges }) {
        nodes[origin] = node
        pending += node
      }
    }
    while (pending.isNotEmpty()) {
      for (edge in pending.removeFirst().incoming.values) {
        if (!edge.isLive() || edge.from in nodes) continue
        val parent = treeStore.node(edge.from) ?: continue
        nodes[edge.from] = parent
        pending += parent
      }
    }
    return nodes
  }

  /** A saved, not deleted edge; an edge only explored has no marking and is never persisted. */
  private fun Edge.isLive(): Boolean = !isDeleted && isGood != null

  /** [edge]'s move as the board spells it, or as stored when its origin cannot be replayed. */
  private fun canonical(edge: Edge): String =
    try {
      GameEngine(edge.from).playSanMove(edge.move)
    } catch (e: RuntimeException) {
      edge.move
    }
}
