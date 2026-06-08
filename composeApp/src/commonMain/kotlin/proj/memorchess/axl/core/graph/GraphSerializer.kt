package proj.memorchess.axl.core.graph

import kotlin.time.Instant
import proj.memorchess.axl.core.data.DataMove
import proj.memorchess.axl.core.data.DataNode
import proj.memorchess.axl.core.data.PositionKey
import proj.memorchess.axl.core.scheduling.CardPhase
import proj.memorchess.axl.core.scheduling.CardState

/**
 * Serializes and deserializes an opening tree graph to or from a deterministic tab separated text
 * format.
 *
 * The format has two sections separated by a blank line:
 * - **Node lines**:
 *   `<index>\t<positionKey>\t<depth>\t<dueDate>\t<lastReview>\t<stability>\t<difficulty>\t<reps>\t<lapses>\t<phase>\t<step>\t<updatedAt>`
 *   where `<lastReview>` is the literal string `null` for a card that has never been reviewed and
 *   `<phase>` is a [proj.memorchess.axl.core.scheduling.CardPhase] name.
 * - **Edge lines**: `<originIndex>\t<destIndex>\t<move>\t<isGood>\t<updatedAt>`
 *
 * Nodes are sorted by [PositionKey.value] and edges by `(originIndex, destIndex, move)` for
 * determinism.
 */
object GraphSerializer {

  private const val TAB = "\t"
  private const val SECTION_SEPARATOR = "\n\n"
  private const val GOOD = "+"
  private const val BAD = "-"
  private const val UNKNOWN = "?"
  private const val NULL_TOKEN = "null"
  private const val NODE_FIELD_COUNT = 12

  /** Serializes nodes to a deterministic text string. Filters out deleted nodes and moves. */
  fun serialize(nodes: List<DataNode>): String {
    val liveNodes = nodes.filter { !it.isDeleted }
    val sorted = liveNodes.sortedBy { it.positionKey.value }
    val indexByKey = buildMap { sorted.forEachIndexed { i, node -> put(node.positionKey, i + 1) } }

    val nodeLines = sorted.mapIndexed { i, node ->
      val idx = i + 1
      val card = node.cardState
      listOf(
          idx,
          node.positionKey.value,
          node.depth,
          card.dueDate,
          card.lastReview?.toString() ?: NULL_TOKEN,
          card.stability,
          card.difficulty,
          card.reps,
          card.lapses,
          card.phase.name,
          card.step,
          node.updatedAt,
        )
        .joinToString(TAB)
    }

    val edges = buildList {
      for (node in sorted) {
        val originIdx = indexByKey[node.positionKey]!!
        for (dataMove in node.previousAndNextMoves.filterNotDeleted().nextMoves.values) {
          val destIdx = indexByKey[dataMove.destination] ?: continue
          add(Edge(originIdx, destIdx, dataMove.move, dataMove.isGood, dataMove.updatedAt))
        }
      }
    }
    val sortedEdges = edges.sortedWith(compareBy({ it.originIndex }, { it.destIndex }, { it.move }))
    val edgeLines = sortedEdges.map { it.toLine() }

    return nodeLines.joinToString("\n") + SECTION_SEPARATOR + edgeLines.joinToString("\n")
  }

  /**
   * Deserializes text back to a list of [DataNode]s. Throws [IllegalArgumentException] on bad
   * input.
   */
  fun deserialize(text: String): List<DataNode> {
    val sections = text.split(SECTION_SEPARATOR)
    require(sections.size == 2) { "Expected two sections separated by a blank line" }

    val nodeSection = sections[0].trim()
    val edgeSection = sections[1].trim()

    val nodeEntries = parseNodeLines(nodeSection)
    val edges = parseEdgeLines(edgeSection, nodeEntries)

    val nextMovesByKey = mutableMapOf<PositionKey, MutableList<DataMove>>()
    val prevMovesByKey = mutableMapOf<PositionKey, MutableList<DataMove>>()

    for (edge in edges) {
      nextMovesByKey.getOrPut(edge.origin) { mutableListOf() }.add(edge.toDataMove())
      prevMovesByKey.getOrPut(edge.destination) { mutableListOf() }.add(edge.toDataMove())
    }

    return nodeEntries.values.map { entry ->
      val key = entry.positionKey
      DataNode(
        positionKey = key,
        previousAndNextMoves =
          PreviousAndNextMoves(
            previousMoves = prevMovesByKey[key].orEmpty(),
            nextMoves = nextMovesByKey[key].orEmpty(),
          ),
        cardState = entry.cardState,
        depth = entry.depth,
        updatedAt = entry.updatedAt,
      )
    }
  }

  private fun parseNodeLines(section: String): Map<Int, NodeEntry> {
    if (section.isEmpty()) return emptyMap()
    return buildMap {
      for (line in section.lines()) {
        val parts = line.split(TAB)
        require(parts.size == NODE_FIELD_COUNT) { "Invalid node line: $line" }
        val index = parts[0].toInt()
        val cardState =
          CardState(
            dueDate = Instant.parse(parts[3]),
            lastReview = if (parts[4] == NULL_TOKEN) null else Instant.parse(parts[4]),
            stability = parts[5].toDouble(),
            difficulty = parts[6].toDouble(),
            reps = parts[7].toInt(),
            lapses = parts[8].toInt(),
            phase = runCatching { CardPhase.valueOf(parts[9]) }.getOrDefault(CardPhase.NEW),
            step = parts[10].toInt(),
          )
        put(
          index,
          NodeEntry(
            positionKey = PositionKey(parts[1]),
            depth = parts[2].toInt(),
            cardState = cardState,
            updatedAt = Instant.parse(parts[11]),
          ),
        )
      }
    }
  }

  private fun parseEdgeLines(section: String, nodeEntries: Map<Int, NodeEntry>): List<ParsedEdge> {
    if (section.isEmpty()) return emptyList()
    return section.lines().map { line ->
      val parts = line.split(TAB)
      require(parts.size == 5) { "Invalid edge line: $line" }
      val originIndex = parts[0].toInt()
      val destIndex = parts[1].toInt()
      require(originIndex in nodeEntries && destIndex in nodeEntries) {
        "Edge references unknown node index in: $line"
      }
      ParsedEdge(
        origin = nodeEntries[originIndex]!!.positionKey,
        destination = nodeEntries[destIndex]!!.positionKey,
        move = parts[2],
        isGood = parseIsGood(parts[3]),
        updatedAt = Instant.parse(parts[4]),
      )
    }
  }

  private fun isGoodToChar(isGood: Boolean?): String =
    when (isGood) {
      true -> GOOD
      false -> BAD
      null -> UNKNOWN
    }

  private fun parseIsGood(char: String): Boolean? =
    when (char) {
      GOOD -> true
      BAD -> false
      UNKNOWN -> null
      else -> throw IllegalArgumentException("Invalid isGood value: $char")
    }

  private data class Edge(
    val originIndex: Int,
    val destIndex: Int,
    val move: String,
    val isGood: Boolean?,
    val updatedAt: Instant,
  ) {
    fun toLine(): String =
      listOf(originIndex, destIndex, move, isGoodToChar(isGood), updatedAt).joinToString(TAB)
  }

  private data class NodeEntry(
    val positionKey: PositionKey,
    val depth: Int,
    val cardState: CardState,
    val updatedAt: Instant,
  )

  private data class ParsedEdge(
    val origin: PositionKey,
    val destination: PositionKey,
    val move: String,
    val isGood: Boolean?,
    val updatedAt: Instant,
  ) {
    fun toDataMove(): DataMove = DataMove(origin, destination, move, isGood, updatedAt = updatedAt)
  }
}
