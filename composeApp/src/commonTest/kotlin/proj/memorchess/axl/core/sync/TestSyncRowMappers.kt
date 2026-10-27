package proj.memorchess.axl.core.sync

import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlin.time.Instant
import proj.memorchess.axl.core.data.DataEdgeRepertoireTag
import proj.memorchess.axl.core.data.DataMove
import proj.memorchess.axl.core.data.DataNode
import proj.memorchess.axl.core.data.DataRepertoire
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

    // DataNode.equals() excludes the fields checked below, so check them explicitly.
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
        ownIsGood = true,
        isDeleted = false,
        updatedAt = now,
        originDevice = "device-1",
        deviceSeq = 3L,
      )

    val row = move.toEdgeSyncRow()!!
    val roundTripped = row.toDataMove()

    // DataMove.equals() excludes the fields checked below, so check them explicitly.
    roundTripped shouldBe move
    roundTripped.originDevice shouldBe move.originDevice
    roundTripped.deviceSeq shouldBe move.deviceSeq
    roundTripped.updatedAt shouldBe move.updatedAt
    row.origin shouldBe "a"
  }

  @Test
  fun aMoveNeverHeldManuallyHasNoEdgeSyncRow() {
    val move =
      DataMove(
        PositionKey("a"),
        PositionKey("b"),
        "e4",
        isGood = true,
        originDevice = "",
        deviceSeq = 0L,
      )

    move.toEdgeSyncRow() shouldBe null
  }

  @Test
  fun anEdgeSyncRowCarriesTheOwnMarkingNotTheEffectiveOne() {
    val move =
      DataMove(
        PositionKey("a"),
        PositionKey("b"),
        "e4",
        isGood = true,
        ownIsGood = false,
        originDevice = "device-1",
        deviceSeq = 3L,
      )

    val row = move.toEdgeSyncRow()!!

    row.isGood shouldBe false
    row.isDeleted shouldBe false
  }

  @Test
  fun aMoveWhoseManualHoldWasRemovedMapsToATombstone() {
    val move =
      DataMove(
        PositionKey("a"),
        PositionKey("b"),
        "e4",
        isGood = true,
        ownIsGood = null,
        originDevice = "device-1",
        deviceSeq = 3L,
      )

    move.toEdgeSyncRow()!!.isDeleted shouldBe true
  }

  @Test
  fun aPulledManualHoldBecomesTheOwnMarking() {
    val row = EdgeSyncRow("a", "b", "e4", isGood = false, isDeleted = false, now, DeviceId("device-1"), 3L)

    row.toDataMove().ownIsGood shouldBe false
    row.copy(isDeleted = true).toDataMove().ownIsGood shouldBe null
  }

  @Test
  fun tagMoveAndMarkingSurviveBothDirections() {
    val tag =
      DataEdgeRepertoireTag(
        PositionKey("a"),
        PositionKey("b"),
        repertoireId = "london",
        move = "d4",
        isGood = true,
        updatedAt = now,
        originDevice = "device-1",
        deviceSeq = 2L,
      )

    tag.toEdgeRepertoireTagSyncRow().toDataEdgeRepertoireTag() shouldBe tag
  }

  @Test
  fun repertoireInstalledVersionSurvivesBothDirections() {
    val repertoire =
      DataRepertoire(
        id = "london",
        name = "London",
        color = null,
        installedVersion = 2,
        updatedAt = now,
        originDevice = "device-1",
        deviceSeq = 2L,
      )

    repertoire.toRepertoireSyncRow().toDataRepertoire() shouldBe repertoire
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
        originDevice = DeviceId("d"),
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
