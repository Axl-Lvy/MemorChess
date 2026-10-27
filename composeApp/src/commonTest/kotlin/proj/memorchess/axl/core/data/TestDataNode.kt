package proj.memorchess.axl.core.data

import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlin.time.Instant
import proj.memorchess.axl.core.graph.PreviousAndNextMoves
import proj.memorchess.axl.core.scheduling.CardStateFactory

/** Tests [DataNode]'s derived-column equality semantics. */
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
    // nextDueNewCard orders by ascending depth, so 0 would jump the queue.
    node.depth shouldBe Int.MAX_VALUE
  }
}
