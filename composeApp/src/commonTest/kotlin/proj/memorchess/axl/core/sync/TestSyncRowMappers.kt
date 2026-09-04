package proj.memorchess.axl.core.sync

import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlin.time.Instant
import proj.memorchess.axl.core.data.DataMove
import proj.memorchess.axl.core.data.DataNode
import proj.memorchess.axl.core.data.PositionKey
import proj.memorchess.axl.core.graph.PreviousAndNextMoves
import proj.memorchess.axl.core.scheduling.CardPhase
import proj.memorchess.axl.core.scheduling.CardState

class TestSyncRowMappers {

  private val now = Instant.parse("2026-01-01T00:00:00Z")

  @Test
  fun dataNodeRoundTripsThroughNodeSyncRow() {
    val node =
      DataNode(
        positionKey = PositionKey("start"),
        previousAndNextMoves = PreviousAndNextMoves(),
        cardState =
          CardState(
            dueDate = now,
            lastReview = now,
            firstReview = now,
            stability = 1.5,
            difficulty = 3.2,
            reps = 4,
            lapses = 1,
            phase = CardPhase.REVIEW,
            step = 2,
          ),
        depth = 7,
        updatedAt = now,
        isDeleted = false,
        hasGoodOutgoing = true,
        createdAt = now,
        originDevice = "device-1",
        deviceSeq = 9L,
      )

    val row = node.toNodeSyncRow()
    val roundTripped =
      row.toDataNode(
        existingMoves = node.previousAndNextMoves,
        existingDepth = node.depth,
        existingHasGoodOutgoing = node.hasGoodOutgoing,
        existingCreatedAt = node.createdAt,
      )

    // DataNode.equals() deliberately excludes depth/hasGoodOutgoing/createdAt/originDevice/
    // deviceSeq/updatedAt (see its own EssentialData), so a plain `shouldBe node` would pass even
    // if the mapper silently dropped one of those. Check them explicitly.
    roundTripped shouldBe node
    roundTripped.depth shouldBe node.depth
    roundTripped.hasGoodOutgoing shouldBe node.hasGoodOutgoing
    roundTripped.createdAt shouldBe node.createdAt
    roundTripped.originDevice shouldBe node.originDevice
    roundTripped.deviceSeq shouldBe node.deviceSeq
    roundTripped.updatedAt shouldBe node.updatedAt
    roundTripped.cardState shouldBe node.cardState
    row.positionKey shouldBe "start"
    row.phase shouldBe "REVIEW"
  }

  @Test
  fun dataMoveRoundTripsThroughEdgeSyncRow() {
    val move =
      DataMove(
        origin = PositionKey("a"),
        destination = PositionKey("b"),
        move = "e4",
        isGood = true,
        isDeleted = false,
        updatedAt = now,
        originDevice = "device-1",
        deviceSeq = 3L,
      )

    val row = move.toEdgeSyncRow()
    val roundTripped = row.toDataMove()

    // DataMove.equals() deliberately excludes originDevice/deviceSeq/updatedAt/createdAt.
    roundTripped shouldBe move
    roundTripped.originDevice shouldBe move.originDevice
    roundTripped.deviceSeq shouldBe move.deviceSeq
    roundTripped.updatedAt shouldBe move.updatedAt
    row.origin shouldBe "a"
  }

  @Test
  fun unrecognizedPhaseFallsBackToNew() {
    val row =
      NodeSyncRow(
        positionKey = "x",
        dueDate = now,
        lastReview = null,
        firstReview = null,
        stability = 0.0,
        difficulty = 0.0,
        reps = 0,
        lapses = 0,
        phase = "NOT_A_REAL_PHASE",
        step = 0,
        isDeleted = false,
        updatedAt = now,
        originDevice = "d",
        deviceSeq = 1L,
      )

    val node =
      row.toDataNode(
        PreviousAndNextMoves(),
        existingDepth = 0,
        existingHasGoodOutgoing = false,
        existingCreatedAt = now,
      )

    node.cardState.phase shouldBe CardPhase.NEW
  }
}
