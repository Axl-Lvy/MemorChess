package proj.memorchess.axl.core.data

import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import proj.memorchess.axl.core.graph.PreviousAndNextMoves
import proj.memorchess.axl.core.scheduling.CardState

/**
 * IndexedDB backed coverage of the sync remote-apply surface, mirroring
 * [TestInMemoryApplyRemote]'s equivalent cases.
 */
class TestIndexedDbApplyRemote {

  private val now = Instant.parse("2026-01-01T00:00:00Z")

  private fun cardState() = CardState(now, null, null, 0.0, 0.0, 0, 0)

  private fun node(key: String, deviceSeq: Long = 1L) =
    DataNode(
      positionKey = PositionKey(key),
      previousAndNextMoves = PreviousAndNextMoves(),
      cardState = cardState(),
      updatedAt = now,
      originDevice = "remote-device",
      deviceSeq = deviceSeq,
    )

  private fun manager(): DatabaseQueryManager = getPlatformSpecificLocalDatabase()

  @Test
  fun applyRemoteNodeWritesWithoutQueuingOutbox() = runTest {
    val db = manager()
    db.eraseAll()

    db.applyRemoteNode(node("a"))

    db.getPosition(PositionKey("a")) shouldBe node("a")
    db.getOutbox() shouldBe emptyList()
  }

  @Test
  fun getPositionIncludingDeletedReturnsATombstone() = runTest {
    val db = manager()
    db.eraseAll()
    db.applyRemoteNode(node("a"))
    db.deletePosition(PositionKey("a"), originDevice = "d", deviceSeq = 2L, updatedAt = now)

    db.getPosition(PositionKey("a")) shouldBe null
    db.getPositionIncludingDeleted(PositionKey("a"))?.isDeleted shouldBe true
  }

  @Test
  fun applyRemoteMoveWritesWithoutQueuingOutbox() = runTest {
    val db = manager()
    db.eraseAll()
    db.applyRemoteNode(node("a"))
    db.applyRemoteNode(node("b"))

    db.applyRemoteMove(
      DataMove(PositionKey("a"), PositionKey("b"), "e4", isGood = true, updatedAt = now)
    )

    db.getPosition(PositionKey("a"))!!.previousAndNextMoves.nextMoves.keys shouldBe setOf("e4")
    db.getPosition(PositionKey("b"))!!.previousAndNextMoves.previousMoves.keys shouldBe
      setOf("e4")
    db.getOutbox() shouldBe emptyList()
  }
}
