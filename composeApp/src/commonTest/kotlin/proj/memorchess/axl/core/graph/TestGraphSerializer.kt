package proj.memorchess.axl.core.graph

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import proj.memorchess.axl.core.data.DataMove
import proj.memorchess.axl.core.data.DataNode
import proj.memorchess.axl.core.data.PositionKey
import proj.memorchess.axl.core.date.PreviousAndNextDate

class TestGraphSerializer {

  private val startPos = PositionKey("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq")
  private val posAfterE4 = PositionKey("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq")
  private val posAfterD4 = PositionKey("rnbqkbnr/pppppppp/8/8/3P4/8/PPP1PPPP/RNBQKBNR b KQkq")
  private val posAfterE4E5 = PositionKey("rnbqkbnr/pppp1ppp/8/4p3/4P3/8/PPPP1PPP/RNBQKBNR w KQkq")

  private val date1 = LocalDate.parse("2026-01-01")
  private val date2 = LocalDate.parse("2026-01-05")
  private val instant1 = Instant.parse("2026-01-01T00:00:00Z")
  private val instant2 = Instant.parse("2026-01-02T10:30:00Z")

  @Test
  fun emptyGraphRoundTrip() {
    val result = GraphSerializer.deserialize(GraphSerializer.serialize(emptyList()))

    result shouldHaveSize 0
  }

  @Test
  fun singleNodeNoEdges() {
    val node =
      DataNode(
        positionKey = startPos,
        previousAndNextMoves = PreviousAndNextMoves(),
        previousAndNextTrainingDate = PreviousAndNextDate(date1, date2),
        depth = 0,
        updatedAt = instant1,
      )

    val result = GraphSerializer.deserialize(GraphSerializer.serialize(listOf(node)))

    result shouldHaveSize 1
    result[0].positionKey shouldBe startPos
    result[0].depth shouldBe 0
    result[0].previousAndNextTrainingDate shouldBe PreviousAndNextDate(date1, date2)
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
        previousAndNextTrainingDate = PreviousAndNextDate(date1, date1),
        depth = 0,
        updatedAt = instant1,
      )
    val e4Node =
      DataNode(
        positionKey = posAfterE4,
        previousAndNextMoves = PreviousAndNextMoves(listOf(move), emptyList()),
        previousAndNextTrainingDate = PreviousAndNextDate(date1, date2),
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
        previousAndNextTrainingDate = PreviousAndNextDate(date1, date1),
        depth = 0,
        updatedAt = instant1,
      )
    val nodes =
      listOf(
        startNode,
        DataNode(
          positionKey = posAfterE4,
          previousAndNextMoves = PreviousAndNextMoves(listOf(goodMove), emptyList()),
          previousAndNextTrainingDate = PreviousAndNextDate(date1, date1),
          depth = 1,
          updatedAt = instant1,
        ),
        DataNode(
          positionKey = posAfterD4,
          previousAndNextMoves = PreviousAndNextMoves(listOf(badMove), emptyList()),
          previousAndNextTrainingDate = PreviousAndNextDate(date1, date1),
          depth = 1,
          updatedAt = instant1,
        ),
        DataNode(
          positionKey = posAfterE4E5,
          previousAndNextMoves = PreviousAndNextMoves(listOf(unknownMove), emptyList()),
          previousAndNextTrainingDate = PreviousAndNextDate(date1, date1),
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
        previousAndNextTrainingDate = PreviousAndNextDate(date1, date1),
        depth = 0,
        updatedAt = instant1,
      )
    val nodeB =
      DataNode(
        positionKey = posAfterE4,
        previousAndNextMoves = PreviousAndNextMoves(listOf(move), emptyList()),
        previousAndNextTrainingDate = PreviousAndNextDate(date1, date1),
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
        previousAndNextTrainingDate = PreviousAndNextDate(date1, date1),
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
          previousAndNextTrainingDate = PreviousAndNextDate(date1, date1),
          depth = 0,
          updatedAt = instant1,
        ),
        DataNode(
          positionKey = posAfterE4,
          previousAndNextMoves = PreviousAndNextMoves(listOf(deletedMove), emptyList()),
          previousAndNextTrainingDate = PreviousAndNextDate(date1, date1),
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
          previousAndNextTrainingDate = PreviousAndNextDate(date1, date1),
          depth = 0,
          updatedAt = instant1,
        ),
        DataNode(
          positionKey = posAfterE4,
          previousAndNextMoves = PreviousAndNextMoves(listOf(moveE4), listOf(moveE5)),
          previousAndNextTrainingDate = PreviousAndNextDate(date1, date2),
          depth = 1,
          updatedAt = instant1,
        ),
        DataNode(
          positionKey = posAfterD4,
          previousAndNextMoves = PreviousAndNextMoves(listOf(moveD4), emptyList()),
          previousAndNextTrainingDate = PreviousAndNextDate(date1, date1),
          depth = 1,
          updatedAt = instant1,
        ),
        DataNode(
          positionKey = posAfterE4E5,
          previousAndNextMoves = PreviousAndNextMoves(listOf(moveE5), emptyList()),
          previousAndNextTrainingDate = PreviousAndNextDate(date1, date2),
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
  fun trainingDatesAndDepthSurviveRoundTrip() {
    val node =
      DataNode(
        positionKey = startPos,
        previousAndNextMoves = PreviousAndNextMoves(),
        previousAndNextTrainingDate = PreviousAndNextDate(date1, date2),
        depth = 7,
        updatedAt = instant2,
      )

    val result = GraphSerializer.deserialize(GraphSerializer.serialize(listOf(node)))

    result[0].previousAndNextTrainingDate.previousDate shouldBe date1
    result[0].previousAndNextTrainingDate.nextDate shouldBe date2
    result[0].depth shouldBe 7
    result[0].updatedAt shouldBe instant2
  }

  @Test
  fun invalidInputThrows() {
    shouldThrow<IllegalArgumentException> { GraphSerializer.deserialize("garbage") }
  }
}
