package proj.memorchess.axl.core.data

import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import proj.memorchess.axl.core.engine.GameEngine
import proj.memorchess.axl.core.graph.GraphSerializer
import proj.memorchess.axl.core.graph.PreviousAndNextMoves
import proj.memorchess.axl.core.scheduling.CardState

/**
 * Storage behavior every disk backed [DatabaseQueryManager] owes for real position keys (stored
 * packed) and malformed ones (stored escaped). Each backend runs it through a subclass.
 */
abstract class PositionKeyStorageContract {

  /** A manager over an empty store. */
  protected abstract suspend fun freshManager(): DatabaseQueryManager

  private val now = Instant.parse("2026-01-01T00:00:00Z")

  private val line = listOf("e4", "d5", "e5", "f5")

  /** Start position then the keys after each move of [line]; the last carries en passant. */
  private val lineKeys: List<PositionKey> = run {
    val engine = GameEngine()
    listOf(engine.toPositionKey()) +
      line.map {
        engine.playSanMove(it)
        engine.toPositionKey()
      }
  }

  private val malformedKeys = listOf("not a fen", "zz", "a b c d e").map(::PositionKey)

  private fun node(key: PositionKey, previous: List<DataMove>, next: List<DataMove>) =
    DataNode(
      positionKey = key,
      previousAndNextMoves = PreviousAndNextMoves(previous, next),
      cardState = CardState(now, null, null, 0.0, 0.0, 0, 0),
      updatedAt = now,
      createdAt = now,
    )

  private fun lineNodes(): List<DataNode> {
    val moves =
      line.indices.map { i ->
        DataMove(
          lineKeys[i],
          lineKeys[i + 1],
          line[i],
          isGood = true,
          createdAt = now,
          updatedAt = now,
        )
      }
    return lineKeys.indices.map { i ->
      node(lineKeys[i], listOfNotNull(moves.getOrNull(i - 1)), listOfNotNull(moves.getOrNull(i)))
    }
  }

  private fun malformedNodes() = malformedKeys.map { node(it, emptyList(), emptyList()) }

  private suspend fun DatabaseQueryManager.drainPages(limit: Int): List<DataNode> {
    val nodes = mutableListOf<DataNode>()
    var cursor: String? = null
    do {
      val page = getNodesPage(cursor, limit)
      nodes += page.nodes
      cursor = page.nextCursor
    } while (cursor != null)
    return nodes
  }

  @Test
  fun realPositionsWithEdgesRoundTrip() = runTest {
    val db = freshManager()
    val nodes = lineNodes()

    db.insertNodes(*nodes.toTypedArray())

    lineKeys.last().value.endsWith(" f6") shouldBe true
    for (node in nodes) db.getPosition(node.positionKey) shouldBe node
  }

  @Test
  fun realPositionTagsRoundTrip() = runTest {
    val db = freshManager()
    val tag =
      DataEdgeRepertoireTag(lineKeys[1], lineKeys[2], "rep", "d5", isGood = true, updatedAt = now)

    db.insertTag(tag)

    db.getTags(lineKeys[1], lineKeys[2]) shouldBe listOf(tag)
    db.getTagsTouching(lineKeys[2]) shouldBe listOf(tag)
  }

  @Test
  fun malformedRemoteKeyRoundTrips() = runTest {
    val db = freshManager()
    val malformed = malformedKeys.first()
    val start = lineKeys.first()
    val move = DataMove(malformed, start, "e4", isGood = true, updatedAt = now)

    db.applyRemoteNode(node(malformed, emptyList(), emptyList()))
    db.applyRemoteNode(node(start, emptyList(), emptyList()))
    db.applyRemoteMove(move)

    db.getPosition(malformed) shouldBe node(malformed, emptyList(), listOf(move))
    db.getPosition(start) shouldBe node(start, listOf(move), emptyList())
  }

  @Test
  fun pagingCrossesBoundaryInEncodedOrder() = runTest {
    val db = freshManager()
    val nodes = lineNodes() + malformedNodes()
    db.insertNodes(*nodes.toTypedArray())

    val paged = db.drainPages(limit = 3).map { it.positionKey }

    paged shouldBe nodes.map { it.positionKey }.sortedWith(PositionKeyCodec.ORDER)
  }

  @Test
  fun streamedExportMatchesEagerSerialize() = runTest {
    val db = freshManager()
    val nodes = lineNodes() + malformedNodes()
    db.insertNodes(*nodes.toTypedArray())

    val streamed = GraphSerializer.serializeStreaming(3) { c, l -> db.getNodesPage(c, l) }

    streamed shouldBe GraphSerializer.serialize(nodes)
  }

  @Test
  fun outboxKeepsTextKeysForRealPositions() = runTest {
    val db = freshManager()
    db.insertNodes(*lineNodes().toTypedArray())
    db.insertTag(
      DataEdgeRepertoireTag(lineKeys[1], lineKeys[2], "rep", "d5", isGood = true, updatedAt = now),
    )

    db.deletePosition(lineKeys.last(), originDevice = "d", deviceSeq = 2L, updatedAt = now)

    db.getOutbox().map { it.key }.toSet() shouldBe
      lineKeys.map { DirtyKey.NodeKey(it) }.toSet() +
        DirtyKey.TagKey(lineKeys[1], lineKeys[2], "rep") +
        DirtyKey.EdgeKey(lineKeys[3], lineKeys[4])
  }
}
