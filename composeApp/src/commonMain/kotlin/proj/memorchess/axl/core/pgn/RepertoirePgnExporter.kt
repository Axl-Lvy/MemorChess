package proj.memorchess.axl.core.pgn

import proj.memorchess.axl.core.data.PositionKey
import proj.memorchess.axl.core.engine.GameEngine
import proj.memorchess.axl.core.graph.Node
import proj.memorchess.axl.core.graph.RepertoireTagStore
import proj.memorchess.axl.core.graph.TreeStore

/** Outcome of [RepertoirePgnExporter.export]. */
sealed class RepertoireExportResult {

  /** The repertoire has no tagged edge at all. */
  data object Empty : RepertoireExportResult()

  /**
   * The exported document.
   *
   * @property text The PGN document, in the format [PgnParser] reads.
   * @property moveCount Distinct `(position, move)` edges included, the same unit
   *   `RepertoirePublishLimits.MAX_REPERTOIRE_MOVES` caps.
   * @property maxPlyDepth Deepest exported line, in plies from the starting position.
   */
  data class Pgn(val text: String, val moveCount: Int, val maxPlyDepth: Int) :
    RepertoireExportResult()
}

/**
 * Turns a repertoire's tagged edges back into a PGN document, the reverse of `PgnImporter`.
 *
 * A line that transposes into already known theory can have one or more untagged edges between the
 * starting position and a tagged one further down (`LinesExplorer` only tags an edge the first time
 * it is newly played). Exporting tagged edges alone would silently drop such a tagged edge, since
 * nothing would connect it back to the root. This keeps an edge when it is itself tagged with
 * [repertoireId] or leads to a position some live tagged edge is reachable from, so every exported
 * line stays whole and playable. Those positions are found by walking back from the tagged edges,
 * so only the repertoire's own lines and their ancestors are loaded, never the rest of the graph. A
 * move back into a position already on the current line is dropped.
 *
 * Does not re run chess legality checking: every edge walked already exists as a legal edge in the
 * local graph. A repertoire's good or bad move classification does not survive the round trip (PGN
 * carries no such marker); `PgnImporter` re derives it on reinstall from the declared side.
 */
class RepertoirePgnExporter(
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
    val distinctMoves = mutableSetOf<Pair<PositionKey, PositionKey>>()
    var maxPly = 0

    fun buildChildren(position: PositionKey, ply: Int): List<PgnMoveNode> {
      if (ply > maxPly) maxPly = ply
      val node = leadingToTagged[position] ?: return emptyList()
      line += position
      val included =
        node.outgoing.values.filter { edge ->
          !edge.isDeleted &&
            edge.to !in line &&
            ((edge.from to edge.to) in taggedEdges || edge.to in leadingToTagged)
        }
      val children = included.map { edge ->
        distinctMoves += edge.from to edge.to
        PgnMoveNode(edge.move, buildChildren(edge.to, ply + 1))
      }
      line -= position
      return children
    }

    val rootKey = GameEngine().toPositionKey()
    val moves = buildChildren(rootKey, ply = 0)
    if (moves.isEmpty()) return RepertoireExportResult.Empty
    return RepertoireExportResult.Pgn(renderMovetext(moves), distinctMoves.size, maxPly)
  }

  /** Every position some live edge of [taggedEdges] is reachable from, mapped to its node. */
  private suspend fun nodesLeadingTo(
    taggedEdges: Set<Pair<PositionKey, PositionKey>>
  ): Map<PositionKey, Node> {
    val nodes = mutableMapOf<PositionKey, Node>()
    val pending = ArrayDeque<Node>()
    for (origin in taggedEdges.mapTo(mutableSetOf()) { it.first }) {
      val node = treeStore.node(origin) ?: continue
      if (node.outgoing.values.any { !it.isDeleted && (it.from to it.to) in taggedEdges }) {
        nodes[origin] = node
        pending += node
      }
    }
    while (pending.isNotEmpty()) {
      for (edge in pending.removeFirst().incoming.values) {
        if (edge.isDeleted || edge.from in nodes) continue
        val parent = treeStore.node(edge.from) ?: continue
        nodes[edge.from] = parent
        pending += parent
      }
    }
    return nodes
  }

  /** Renders [moves] as SAN movetext with numbered moves and parenthesized RAV variations. */
  private fun renderMovetext(moves: List<PgnMoveNode>): String {
    val sb = StringBuilder()
    renderLine(moves, ply = 0, sb)
    return sb.toString().trim()
  }

  private fun renderLine(moves: List<PgnMoveNode>, ply: Int, sb: StringBuilder) {
    if (moves.isEmpty()) return
    val moveNumber = ply / 2 + 1
    val marker = if (ply % 2 == 0) "." else "..."
    val mainline = moves[0]
    sb.append(moveNumber).append(marker).append(' ').append(mainline.san).append(' ')
    for (alternative in moves.drop(1)) {
      sb.append('(')
      sb.append(moveNumber).append(marker).append(' ').append(alternative.san).append(' ')
      renderLine(alternative.children, ply + 1, sb)
      sb.append(") ")
    }
    renderLine(mainline.children, ply + 1, sb)
  }
}
