package proj.memorchess.axl.core.data

import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlin.time.Instant
import proj.memorchess.axl.core.graph.PreviousAndNextMoves
import proj.memorchess.axl.core.scheduling.CardPhase
import proj.memorchess.axl.core.scheduling.CardState

/**
 * Verifies that the Room entity mapping round-trips the FSRS [CardState], including the Phase 3
 * [CardPhase] and learning step. This is the persistence consumer of the state machine: a bug in
 * the column mapping would silently reset every card to [CardPhase.NEW] on reload.
 */
class TestNodeWithMovesMapping {

  private val due = Instant.parse("2026-05-20T10:00:00Z")

  private fun roundTrip(card: CardState): CardState {
    val dataNode = DataNode(PositionKey("posA b K"), PreviousAndNextMoves(), card)
    return NodeWithMoves.convertToEntity(dataNode).toStoredNode().cardState
  }

  @Test
  fun relearningStateSurvivesTheRoundTrip() {
    val card =
      CardState(
        dueDate = due,
        lastReview = due,
        firstReview = due,
        stability = 3.5,
        difficulty = 6.0,
        reps = 4,
        lapses = 2,
        phase = CardPhase.RELEARNING,
        step = 1,
      )
    roundTrip(card) shouldBe card
  }

  @Test
  fun moveCreatedAtSurvivesTheRoundTrip() {
    val createdAt = Instant.parse("2026-05-01T08:00:00Z")
    val move =
      DataMove(
        origin = PositionKey("posA b K"),
        destination = PositionKey("posB w K"),
        move = "e4",
        isGood = true,
        createdAt = createdAt,
        updatedAt = due,
      )
    val roundTripped = MoveEntity.convertToEntity(move).toStoredMove()
    roundTripped.createdAt shouldBe createdAt
    roundTripped.updatedAt shouldBe due
  }

  @Test
  fun everyPhaseRoundTripsToItself() {
    for (phase in CardPhase.entries) {
      val card =
        CardState(
          dueDate = due,
          lastReview = due,
          firstReview = due,
          stability = 1.0,
          difficulty = 5.0,
          reps = 1,
          lapses = 0,
          phase = phase,
          step = 0,
        )
      roundTrip(card).phase shouldBe phase
    }
  }

  private val defaultCard =
    CardState(
      dueDate = due,
      lastReview = due,
      firstReview = due,
      stability = 1.0,
      difficulty = 5.0,
      reps = 1,
      lapses = 0,
      phase = CardPhase.NEW,
      step = 0,
    )

  private fun move(isGood: Boolean, isDeleted: Boolean = false) =
    DataMove(
      origin = PositionKey("posA b K"),
      destination = PositionKey("posB w K"),
      move = "e4",
      isGood = isGood,
      isDeleted = isDeleted,
    )

  private fun hasGoodOutgoingAfterConversion(nextMoves: List<DataMove>): Boolean {
    val dataNode =
      DataNode(
        PositionKey("posA b K"),
        PreviousAndNextMoves(previousMoves = emptyList(), nextMoves = nextMoves),
        defaultCard,
      )
    return NodeWithMoves.convertToEntity(dataNode).node.hasGoodOutgoing
  }

  /**
   * This is the regression covered by an imported repertoire: [GraphSerializer] never sets
   * [DataNode.hasGoodOutgoing], relying on its default of `false`, even though the imported node
   * carries a real good outgoing move. The entity mapping must derive the flag from the moves it
   * already holds instead of trusting the caller's value verbatim.
   */
  @Test
  fun aGoodNonDeletedNextMoveMakesTheFlagTrueEvenWhenTheCallerOmittedIt() {
    hasGoodOutgoingAfterConversion(listOf(move(isGood = true))) shouldBe true
  }

  @Test
  fun noNextMovesMakesTheFlagFalse() {
    hasGoodOutgoingAfterConversion(emptyList()) shouldBe false
  }

  @Test
  fun aBadNextMoveAloneMakesTheFlagFalse() {
    hasGoodOutgoingAfterConversion(listOf(move(isGood = false))) shouldBe false
  }

  @Test
  fun aGoodButDeletedNextMoveMakesTheFlagFalse() {
    hasGoodOutgoingAfterConversion(listOf(move(isGood = true, isDeleted = true))) shouldBe false
  }

  @Test
  fun aMixOfDeletedGoodAndLiveGoodMovesMakesTheFlagTrue() {
    hasGoodOutgoingAfterConversion(
      listOf(move(isGood = true, isDeleted = true), move(isGood = false))
    ) shouldBe false
    hasGoodOutgoingAfterConversion(
      listOf(move(isGood = true, isDeleted = true), move(isGood = true))
    ) shouldBe true
  }

  @Test
  fun aCallerSuppliedTrueFlagIsIgnoredWhenNoMoveSupportsIt() {
    val dataNode =
      DataNode(
        PositionKey("posA b K"),
        PreviousAndNextMoves(),
        defaultCard,
        hasGoodOutgoing = true,
      )
    NodeWithMoves.convertToEntity(dataNode).node.hasGoodOutgoing shouldBe false
  }
}
