package proj.memorchess.axl.core.graph

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import proj.memorchess.axl.core.data.DataMove
import proj.memorchess.axl.core.data.DataNode
import proj.memorchess.axl.core.data.NodesPage
import proj.memorchess.axl.core.data.PositionKey
import proj.memorchess.axl.core.scheduling.CardPhase
import proj.memorchess.axl.core.scheduling.CardState

class TestGraphSerializer {

  private val startPos = PositionKey("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq")
  private val posAfterE4 = PositionKey("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq")
  private val posAfterD4 = PositionKey("rnbqkbnr/pppppppp/8/8/3P4/8/PPP1PPPP/RNBQKBNR b KQkq")
  private val posAfterE4E5 = PositionKey("rnbqkbnr/pppp1ppp/8/4p3/4P3/8/PPPP1PPP/RNBQKBNR w KQkq")

  private val instant1 = Instant.parse("2026-01-01T00:00:00Z")
  private val instant2 = Instant.parse("2026-01-02T10:30:00Z")
  private val instant3 = Instant.parse("2026-01-05T00:00:00Z")

  private fun cardState(due: Instant, lastReview: Instant? = null): CardState =
    CardState(
      dueDate = due,
      lastReview = lastReview,
      firstReview = lastReview,
      stability = 0.0,
      difficulty = 0.0,
      reps = 0,
      lapses = 0,
    )

  @Test
  fun emptyGraphRoundTrip() {
    val result = GraphSerializer.deserialize(GraphSerializer.serialize(emptyList()))

    result shouldHaveSize 0
  }

  @Test
  fun singleNodeNoEdges() {
    val state = cardState(instant3, instant1)
    val node =
      DataNode(
        positionKey = startPos,
        previousAndNextMoves = PreviousAndNextMoves(),
        cardState = state,
        depth = 0,
        updatedAt = instant1,
      )

    val result = GraphSerializer.deserialize(GraphSerializer.serialize(listOf(node)))

    result shouldHaveSize 1
    result[0].positionKey shouldBe startPos
    result[0].depth shouldBe 0
    result[0].cardState shouldBe state
    result[0].previousAndNextMoves.nextMoves.size shouldBe 0
    result[0].previousAndNextMoves.previousMoves.size shouldBe 0
  }

  @Test
  fun twoNodesWithOneEdge() {
    val move = DataMove(startPos, posAfterE4, "e4", isGood = true, updatedAt = instant2)
    val startNode =
      DataNode(
        positionKey = startPos,
        previousAndNextMoves = PreviousAndNextMoves(emptyList(), listOf(move)),
        cardState = cardState(instant1, instant1),
        depth = 0,
        updatedAt = instant1,
      )
    val e4Node =
      DataNode(
        positionKey = posAfterE4,
        previousAndNextMoves = PreviousAndNextMoves(listOf(move), emptyList()),
        cardState = cardState(instant3, instant1),
        depth = 1,
        updatedAt = instant2,
      )

    val result = GraphSerializer.deserialize(GraphSerializer.serialize(listOf(startNode, e4Node)))

    result shouldHaveSize 2
    val resultStart = result.first { it.positionKey == startPos }
    val resultE4 = result.first { it.positionKey == posAfterE4 }
    resultStart.previousAndNextMoves.nextMoves.size shouldBe 1
    resultStart.previousAndNextMoves.nextMoves["e4"]!!.destination shouldBe posAfterE4
    resultE4.previousAndNextMoves.previousMoves.size shouldBe 1
    resultE4.previousAndNextMoves.previousMoves["e4"]!!.origin shouldBe startPos
  }

  @Test
  fun isGoodVariationsRoundTrip() {
    val goodMove = DataMove(startPos, posAfterE4, "e4", isGood = true, updatedAt = instant1)
    val badMove = DataMove(startPos, posAfterD4, "d4", isGood = false, updatedAt = instant1)
    val unknownMove = DataMove(startPos, posAfterE4E5, "Nf3", isGood = null, updatedAt = instant1)
    val startNode =
      DataNode(
        positionKey = startPos,
        previousAndNextMoves =
          PreviousAndNextMoves(emptyList(), listOf(goodMove, badMove, unknownMove)),
        cardState = cardState(instant1),
        depth = 0,
        updatedAt = instant1,
      )
    val nodes =
      listOf(
        startNode,
        DataNode(
          positionKey = posAfterE4,
          previousAndNextMoves = PreviousAndNextMoves(listOf(goodMove), emptyList()),
          cardState = cardState(instant1),
          depth = 1,
          updatedAt = instant1,
        ),
        DataNode(
          positionKey = posAfterD4,
          previousAndNextMoves = PreviousAndNextMoves(listOf(badMove), emptyList()),
          cardState = cardState(instant1),
          depth = 1,
          updatedAt = instant1,
        ),
        DataNode(
          positionKey = posAfterE4E5,
          previousAndNextMoves = PreviousAndNextMoves(listOf(unknownMove), emptyList()),
          cardState = cardState(instant1),
          depth = 1,
          updatedAt = instant1,
        ),
      )

    val result = GraphSerializer.deserialize(GraphSerializer.serialize(nodes))

    val resultStart = result.first { it.positionKey == startPos }
    resultStart.previousAndNextMoves.nextMoves["e4"]!!.isGood shouldBe true
    resultStart.previousAndNextMoves.nextMoves["d4"]!!.isGood shouldBe false
    resultStart.previousAndNextMoves.nextMoves["Nf3"]!!.isGood shouldBe null
  }

  @Test
  fun determinismDifferentInsertionOrders() {
    val move = DataMove(startPos, posAfterE4, "e4", isGood = true, updatedAt = instant1)
    val nodeA =
      DataNode(
        positionKey = startPos,
        previousAndNextMoves = PreviousAndNextMoves(emptyList(), listOf(move)),
        cardState = cardState(instant1),
        depth = 0,
        updatedAt = instant1,
      )
    val nodeB =
      DataNode(
        positionKey = posAfterE4,
        previousAndNextMoves = PreviousAndNextMoves(listOf(move), emptyList()),
        cardState = cardState(instant1),
        depth = 1,
        updatedAt = instant1,
      )

    val output1 = GraphSerializer.serialize(listOf(nodeA, nodeB))
    val output2 = GraphSerializer.serialize(listOf(nodeB, nodeA))

    output1 shouldBe output2
  }

  @Test
  fun deletedNodesExcluded() {
    val node =
      DataNode(
        positionKey = startPos,
        previousAndNextMoves = PreviousAndNextMoves(),
        cardState = cardState(instant1),
        depth = 0,
        updatedAt = instant1,
        isDeleted = true,
      )

    val serialized = GraphSerializer.serialize(listOf(node))
    val result = GraphSerializer.deserialize(serialized)

    result shouldHaveSize 0
  }

  @Test
  fun deletedMovesExcluded() {
    val deletedMove =
      DataMove(startPos, posAfterE4, "e4", isGood = true, isDeleted = true, updatedAt = instant1)
    val nodes =
      listOf(
        DataNode(
          positionKey = startPos,
          previousAndNextMoves = PreviousAndNextMoves(emptyList(), listOf(deletedMove)),
          cardState = cardState(instant1),
          depth = 0,
          updatedAt = instant1,
        ),
        DataNode(
          positionKey = posAfterE4,
          previousAndNextMoves = PreviousAndNextMoves(listOf(deletedMove), emptyList()),
          cardState = cardState(instant1),
          depth = 1,
          updatedAt = instant1,
        ),
      )

    val result = GraphSerializer.deserialize(GraphSerializer.serialize(nodes))

    val resultStart = result.first { it.positionKey == startPos }
    resultStart.previousAndNextMoves.nextMoves.size shouldBe 0
  }

  @Test
  fun complexGraphWithBranchingAndConvergence() {
    val moveE4 = DataMove(startPos, posAfterE4, "e4", isGood = true, updatedAt = instant1)
    val moveD4 = DataMove(startPos, posAfterD4, "d4", isGood = true, updatedAt = instant1)
    val moveE5 = DataMove(posAfterE4, posAfterE4E5, "e5", isGood = true, updatedAt = instant2)
    val nodes =
      listOf(
        DataNode(
          positionKey = startPos,
          previousAndNextMoves = PreviousAndNextMoves(emptyList(), listOf(moveE4, moveD4)),
          cardState = cardState(instant1),
          depth = 0,
          updatedAt = instant1,
        ),
        DataNode(
          positionKey = posAfterE4,
          previousAndNextMoves = PreviousAndNextMoves(listOf(moveE4), listOf(moveE5)),
          cardState = cardState(instant3, instant1),
          depth = 1,
          updatedAt = instant1,
        ),
        DataNode(
          positionKey = posAfterD4,
          previousAndNextMoves = PreviousAndNextMoves(listOf(moveD4), emptyList()),
          cardState = cardState(instant1),
          depth = 1,
          updatedAt = instant1,
        ),
        DataNode(
          positionKey = posAfterE4E5,
          previousAndNextMoves = PreviousAndNextMoves(listOf(moveE5), emptyList()),
          cardState = cardState(instant3, instant1),
          depth = 2,
          updatedAt = instant2,
        ),
      )

    val result = GraphSerializer.deserialize(GraphSerializer.serialize(nodes))

    result shouldHaveSize 4
    val resultStart = result.first { it.positionKey == startPos }
    resultStart.previousAndNextMoves.nextMoves.size shouldBe 2
    val resultE4 = result.first { it.positionKey == posAfterE4 }
    resultE4.previousAndNextMoves.previousMoves.size shouldBe 1
    resultE4.previousAndNextMoves.nextMoves.size shouldBe 1
    val resultE4E5 = result.first { it.positionKey == posAfterE4E5 }
    resultE4E5.depth shouldBe 2
  }

  @Test
  fun cardStateAndDepthSurviveRoundTrip() {
    // Non-default phase and step so the round trip would catch a dropped column: a card mid
    // relearning must not silently reset to NEW/0 on export then import.
    val state =
      CardState(
        dueDate = instant3,
        lastReview = instant2,
        firstReview = instant1,
        stability = 12.5,
        difficulty = 6.25,
        reps = 4,
        lapses = 1,
        phase = CardPhase.RELEARNING,
        step = 1,
      )
    val node =
      DataNode(
        positionKey = startPos,
        previousAndNextMoves = PreviousAndNextMoves(),
        cardState = state,
        depth = 7,
        updatedAt = instant2,
      )

    val result = GraphSerializer.deserialize(GraphSerializer.serialize(listOf(node)))

    result[0].cardState shouldBe state
    result[0].depth shouldBe 7
    result[0].updatedAt shouldBe instant2
  }

  @Test
  fun nullFirstReviewSurvivesRoundTrip() {
    // A brand new card has a null firstReview; the round trip must keep it null rather than
    // inventing a date.
    val state = cardState(instant3, lastReview = null)
    val node =
      DataNode(
        positionKey = startPos,
        previousAndNextMoves = PreviousAndNextMoves(),
        cardState = state,
        depth = 0,
        updatedAt = instant1,
      )

    val result = GraphSerializer.deserialize(GraphSerializer.serialize(listOf(node)))

    result[0].cardState.firstReview shouldBe null
    result[0].cardState shouldBe state
  }

  @Test
  fun everyCardPhaseSurvivesRoundTrip() {
    for (phase in CardPhase.entries) {
      val state =
        CardState(
          dueDate = instant3,
          lastReview = instant2,
          firstReview = instant1,
          stability = 1.0,
          difficulty = 5.0,
          reps = 1,
          lapses = 0,
          phase = phase,
          step = 0,
        )
      val node =
        DataNode(
          positionKey = startPos,
          previousAndNextMoves = PreviousAndNextMoves(),
          cardState = state,
          depth = 0,
          updatedAt = instant2,
        )

      val result = GraphSerializer.deserialize(GraphSerializer.serialize(listOf(node)))

      result[0].cardState.phase shouldBe phase
    }
  }

  @Test
  fun edgeCreatedAtSurvivesRoundTrip() {
    // DataMove equality ignores timestamps, so the round trip is asserted on the field itself.
    val move =
      DataMove(
        startPos,
        posAfterE4,
        "e4",
        isGood = true,
        createdAt = instant1,
        updatedAt = instant2,
      )
    val nodes =
      listOf(
        DataNode(
          positionKey = startPos,
          previousAndNextMoves = PreviousAndNextMoves(emptyList(), listOf(move)),
          cardState = cardState(instant1),
          depth = 0,
          updatedAt = instant1,
        ),
        DataNode(
          positionKey = posAfterE4,
          previousAndNextMoves = PreviousAndNextMoves(listOf(move), emptyList()),
          cardState = cardState(instant1),
          depth = 1,
          updatedAt = instant1,
        ),
      )

    val result = GraphSerializer.deserialize(GraphSerializer.serialize(nodes))

    val resultStart = result.first { it.positionKey == startPos }
    resultStart.previousAndNextMoves.nextMoves["e4"]!!.createdAt shouldBe instant1
    resultStart.previousAndNextMoves.nextMoves["e4"]!!.updatedAt shouldBe instant2
  }

  @Test
  fun invalidInputThrows() {
    shouldThrow<IllegalArgumentException> { GraphSerializer.deserialize("garbage") }
  }

  @Test
  fun missingBlankLineSeparatorThrows() {
    shouldThrow<IllegalArgumentException> { GraphSerializer.deserialize("single section only") }
  }

  @Test
  fun nodeLineWithWrongColumnCountThrows() {
    val input = "1\tpos\t0\n\n"
    shouldThrow<IllegalArgumentException> { GraphSerializer.deserialize(input) }
  }

  @Test
  fun edgeLineWithWrongColumnCountThrows() {
    val input = "${validNodeLine(1, startPos)}\n\n1\t2"
    shouldThrow<Exception> { GraphSerializer.deserialize(input) }
  }

  @Test
  fun edgeReferencingUnknownNodeThrows() {
    val input =
      "${validNodeLine(1, startPos)}\n\n" +
        "1\t99\te4\t+\t2026-01-01T00:00:00Z\t2026-01-01T00:00:00Z"
    shouldThrow<IllegalArgumentException> { GraphSerializer.deserialize(input) }
  }

  @Test
  fun invalidIsGoodValueThrows() {
    val input =
      "${validNodeLine(1, startPos)}\n${validNodeLine(2, posAfterE4)}\n\n" +
        "1\t2\te4\tX\t2026-01-01T00:00:00Z\t2026-01-01T00:00:00Z"
    shouldThrow<IllegalArgumentException> { GraphSerializer.deserialize(input) }
  }

  private fun validNodeLine(index: Int, positionKey: PositionKey): String =
    "$index\t${positionKey.value}\t0\t2026-01-01T00:00:00Z\tnull\t0.0\t0.0\t0\t0\tNEW\t0\t" +
      "2026-01-01T00:00:00Z"

  @Test
  fun emptyStringThrows() {
    shouldThrow<IllegalArgumentException> { GraphSerializer.deserialize("") }
  }

  /**
   * Turns an in memory node list into a bounded page producer that mimics
   * [proj.memorchess.axl.core.data.DatabaseQueryManager.getNodesPage]: live rows ordered by
   * position key ascending, returning `positionKey > cursor` capped at the page size, with
   * `nextCursor` set only when a full page was returned.
   */
  private fun pagedProducer(
    nodes: List<DataNode>
  ): suspend (cursor: String?, limit: Int) -> NodesPage {
    val sorted = nodes.filter { !it.isDeleted }.sortedBy { it.positionKey.value }
    return { cursor, limit ->
      val page = sorted.filter { cursor == null || it.positionKey.value > cursor }.take(limit)
      val nextCursor = if (page.size == limit) page.last().positionKey.value else null
      NodesPage(page, nextCursor)
    }
  }

  private val complexGraphNodes: List<DataNode>
    get() {
      val moveE4 = DataMove(startPos, posAfterE4, "e4", isGood = true, updatedAt = instant1)
      val moveD4 = DataMove(startPos, posAfterD4, "d4", isGood = true, updatedAt = instant1)
      val moveE5 = DataMove(posAfterE4, posAfterE4E5, "e5", isGood = true, updatedAt = instant2)
      return listOf(
        DataNode(
          positionKey = startPos,
          previousAndNextMoves = PreviousAndNextMoves(emptyList(), listOf(moveE4, moveD4)),
          cardState = cardState(instant1),
          depth = 0,
          updatedAt = instant1,
        ),
        DataNode(
          positionKey = posAfterE4,
          previousAndNextMoves = PreviousAndNextMoves(listOf(moveE4), listOf(moveE5)),
          cardState = cardState(instant3, instant1),
          depth = 1,
          updatedAt = instant1,
        ),
        DataNode(
          positionKey = posAfterD4,
          previousAndNextMoves = PreviousAndNextMoves(listOf(moveD4), emptyList()),
          cardState = cardState(instant1),
          depth = 1,
          updatedAt = instant1,
        ),
        DataNode(
          positionKey = posAfterE4E5,
          previousAndNextMoves = PreviousAndNextMoves(listOf(moveE5), emptyList()),
          cardState = cardState(instant3, instant1),
          depth = 2,
          updatedAt = instant2,
        ),
      )
    }

  @Test
  fun streamedExportIsByteIdenticalToEagerSerialize() = runTest {
    val nodes = complexGraphNodes
    val expected = GraphSerializer.serialize(nodes)

    // Several page sizes, including one that splits the store across multiple pages, one that holds
    // the whole store in a single page, an exact divisor that ends on a trailing empty page, and a
    // limit larger than the store.
    for (pageSize in listOf(1, 2, nodes.size, nodes.size + 5)) {
      val streamed = GraphSerializer.serializeStreaming(pageSize, pagedProducer(nodes))
      streamed shouldBe expected
    }
  }

  @Test
  fun streamedExportRoundTripsThroughDeserialize() = runTest {
    val nodes = complexGraphNodes

    val streamed = GraphSerializer.serializeStreaming(2, pagedProducer(nodes))
    val result = GraphSerializer.deserialize(streamed)

    result shouldHaveSize 4
    val resultStart = result.first { it.positionKey == startPos }
    resultStart.previousAndNextMoves.nextMoves.size shouldBe 2
  }

  @Test
  fun streamedExportOfEmptyStoreIsByteIdentical() = runTest {
    val expected = GraphSerializer.serialize(emptyList())

    val streamed = GraphSerializer.serializeStreaming(3, pagedProducer(emptyList()))

    streamed shouldBe expected
  }

  @Test
  fun streamedExportExcludesDeletedNodesAndMoves() = runTest {
    val deletedMove =
      DataMove(startPos, posAfterE4, "e4", isGood = true, isDeleted = true, updatedAt = instant1)
    val nodes =
      listOf(
        DataNode(
          positionKey = startPos,
          previousAndNextMoves = PreviousAndNextMoves(emptyList(), listOf(deletedMove)),
          cardState = cardState(instant1),
          depth = 0,
          updatedAt = instant1,
        ),
        DataNode(
          positionKey = posAfterE4,
          previousAndNextMoves = PreviousAndNextMoves(listOf(deletedMove), emptyList()),
          cardState = cardState(instant1),
          depth = 1,
          updatedAt = instant1,
        ),
      )
    val expected = GraphSerializer.serialize(nodes)

    val streamed = GraphSerializer.serializeStreaming(1, pagedProducer(nodes))

    streamed shouldBe expected
  }

  @Test
  fun streamedExportRejectsNonPositivePageSize() = runTest {
    shouldThrow<IllegalArgumentException> {
      GraphSerializer.serializeStreaming(0, pagedProducer(complexGraphNodes))
    }
  }
}
