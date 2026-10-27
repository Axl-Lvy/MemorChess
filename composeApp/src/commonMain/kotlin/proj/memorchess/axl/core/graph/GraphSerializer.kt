package proj.memorchess.axl.core.graph

import kotlin.time.Instant
import proj.memorchess.axl.core.data.DataMove
import proj.memorchess.axl.core.data.DataNode
import proj.memorchess.axl.core.data.NodesPage
import proj.memorchess.axl.core.data.PositionKey
import proj.memorchess.axl.core.scheduling.CardPhase
import proj.memorchess.axl.core.scheduling.CardState

/**
 * Converts an opening tree to and from a deterministic tab separated text format: sorted node
 * lines, a blank line, then sorted edge lines.
 */
object GraphSerializer {

  private const val TAB = "\t"
  private const val SECTION_SEPARATOR = "\n\n"
  private const val GOOD = "+"
  private const val BAD = "-"
  private const val UNKNOWN = "?"
  private const val NULL_TOKEN = "null"
  private const val NODE_FIELD_COUNT = 13
  private const val EDGE_FIELD_COUNT = 6

  // The format carries no device identity; TreeStore.importNodes restamps these placeholders.
  private const val PLACEHOLDER_ORIGIN_DEVICE = ""
  private const val PLACEHOLDER_DEVICE_SEQ = 0L

  /** Serializes nodes to a deterministic text string. Filters out deleted nodes and moves. */
  fun serialize(nodes: List<DataNode>): String {
    val liveNodes = nodes.filter { !it.isDeleted }
    val sorted = liveNodes.sortedBy { it.positionKey.value }
    val indexByKey = buildMap { sorted.forEachIndexed { i, node -> put(node.positionKey, i + 1) } }

    val nodeLines = sorted.mapIndexed { i, node -> nodeLine(i + 1, node) }

    val edges = buildList {
      for (node in sorted) {
        addEdgesOf(node, indexByKey[node.positionKey]!!, indexByKey)
      }
    }
    val edgeLines = sortEdges(edges).map { it.toLine() }

    return nodeLines.joinToString("\n") + SECTION_SEPARATOR + edgeLines.joinToString("\n")
  }

  /**
   * Produces the same text as [serialize] over the whole store, reading it through [pageProducer]
   * in pages of [pageSize] instead of holding every row in memory.
   *
   * @throws IllegalArgumentException when [pageSize] is not strictly positive.
   */
  suspend fun serializeStreaming(
    pageSize: Int,
    pageProducer: suspend (cursor: String?, limit: Int) -> NodesPage,
  ): String {
    require(pageSize > 0) { "pageSize must be strictly positive, was $pageSize" }

    val nodeLines = StringBuilder()
    val indexByKey = mutableMapOf<PositionKey, Int>()
    var index = 0
    forEachPage(pageSize, pageProducer) { page ->
      for (node in page.nodes) {
        index += 1
        indexByKey[node.positionKey] = index
        if (nodeLines.isNotEmpty()) nodeLines.append("\n")
        nodeLines.append(nodeLine(index, node))
      }
    }

    val edges = mutableListOf<Edge>()
    forEachPage(pageSize, pageProducer) { page ->
      for (node in page.nodes) {
        val originIdx = indexByKey[node.positionKey] ?: continue
        edges.addEdgesOf(node, originIdx, indexByKey)
      }
    }
    val edgeLines = sortEdges(edges).joinToString("\n") { it.toLine() }

    return nodeLines.toString() + SECTION_SEPARATOR + edgeLines
  }

  private suspend inline fun forEachPage(
    pageSize: Int,
    pageProducer: suspend (cursor: String?, limit: Int) -> NodesPage,
    block: (NodesPage) -> Unit,
  ) {
    var cursor: String? = null
    do {
      val page = pageProducer(cursor, pageSize)
      block(page)
      cursor = page.nextCursor
    } while (cursor != null)
  }

  /** Node line for [node] at the one based [index]. */
  private fun nodeLine(index: Int, node: DataNode): String {
    val card = node.cardState
    return listOf(
        index,
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
        card.firstReview?.toString() ?: NULL_TOKEN,
      )
      .joinToString(TAB)
  }

  /** Appends [node]'s live outgoing edges whose destination is in [indexByKey]. */
  private fun MutableList<Edge>.addEdgesOf(
    node: DataNode,
    originIdx: Int,
    indexByKey: Map<PositionKey, Int>,
  ) {
    for (dataMove in node.previousAndNextMoves.filterNotDeleted().nextMoves.values) {
      val destIdx = indexByKey[dataMove.destination] ?: continue
      add(
        Edge(
          originIdx,
          destIdx,
          dataMove.move,
          dataMove.isGood,
          dataMove.updatedAt,
          dataMove.createdAt,
        )
      )
    }
  }

  private fun sortEdges(edges: List<Edge>): List<Edge> =
    edges.sortedWith(compareBy({ it.originIndex }, { it.destIndex }, { it.move }))

  /**
   * Parses [serialize] output, throwing [IllegalArgumentException] on bad input. Rows carry
   * placeholder device identity, so persist them through [TreeStore.importNodes], which restamps
   * them.
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
        originDevice = PLACEHOLDER_ORIGIN_DEVICE,
        deviceSeq = PLACEHOLDER_DEVICE_SEQ,
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
            firstReview = if (parts[12] == NULL_TOKEN) null else Instant.parse(parts[12]),
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
      require(parts.size == EDGE_FIELD_COUNT) { "Invalid edge line: $line" }
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
        createdAt = Instant.parse(parts[5]),
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
    val createdAt: Instant,
  ) {
    fun toLine(): String =
      listOf(originIndex, destIndex, move, isGoodToChar(isGood), updatedAt, createdAt)
        .joinToString(TAB)
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
    val createdAt: Instant,
  ) {
    fun toDataMove(): DataMove =
      DataMove(
        origin,
        destination,
        move,
        isGood,
        createdAt = createdAt,
        updatedAt = updatedAt,
        originDevice = PLACEHOLDER_ORIGIN_DEVICE,
        deviceSeq = PLACEHOLDER_DEVICE_SEQ,
      )
  }
}
