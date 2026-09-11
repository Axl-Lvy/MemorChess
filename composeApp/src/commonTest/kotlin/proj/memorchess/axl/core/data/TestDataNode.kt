package proj.memorchess.axl.core.data

import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlin.time.Instant
import proj.memorchess.axl.core.graph.PreviousAndNextMoves
import proj.memorchess.axl.core.scheduling.CardStateFactory

/** Tests for [DataNode], focused on its derived-column equality semantics. */
class TestDataNode {

  @Test
  fun derivedColumnsAreExcludedFromEquality() {
    val base = DataNode(PositionKey.START_POSITION, PreviousAndNextMoves(), CardStateFactory.new())
    val withDerived = base.copy(hasGoodOutgoing = true, createdAt = Instant.fromEpochSeconds(123))
    withDerived shouldBe base
    withDerived.hashCode() shouldBe base.hashCode()
  }

  @Test
  fun omittedDepthDefaultsToTheSentinelThatSortsLastInTheNewCardQueue() {
    val node = DataNode(PositionKey.START_POSITION, PreviousAndNextMoves(), CardStateFactory.new())
    // nextDueNewCard orders ascending by depth, so an unset value must be the largest possible
    // depth rather than the smallest: 0 would jump every real position to the front of the queue.
    node.depth shouldBe Int.MAX_VALUE
  }
}
