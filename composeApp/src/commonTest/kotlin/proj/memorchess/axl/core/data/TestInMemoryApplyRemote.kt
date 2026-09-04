package proj.memorchess.axl.core.data

import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import proj.memorchess.axl.core.graph.PreviousAndNextMoves
import proj.memorchess.axl.core.scheduling.CardState

class TestInMemoryApplyRemote {

  private val now = Instant.parse("2026-01-01T00:00:00Z")

  private fun cardState() =
    CardState(
      dueDate = now,
      lastReview = null,
      firstReview = null,
      stability = 0.0,
      difficulty = 0.0,
      reps = 0,
      lapses = 0,
    )

  private fun node(key: String, deviceSeq: Long = 1L) =
    DataNode(
      positionKey = PositionKey(key),
      previousAndNextMoves = PreviousAndNextMoves(),
      cardState = cardState(),
      updatedAt = now,
      originDevice = "remote-device",
      deviceSeq = deviceSeq,
    )

  @Test
  fun applyRemoteNodeWritesWithoutQueuingOutbox() = runTest {
    val db = InMemoryDatabaseQueryManager()

    db.applyRemoteNode(node("a"))

    db.getPosition(PositionKey("a")) shouldBe node("a")
    db.getOutbox() shouldBe emptyList()
  }

  @Test
  fun applyRemoteNodePreservesExistingMoves() = runTest {
    val db = InMemoryDatabaseQueryManager()
    db.insertNodes(node("a"), node("b"))
    db.applyRemoteMove(
      DataMove(PositionKey("a"), PositionKey("b"), "e4", isGood = true, updatedAt = now)
    )

    // A remote node update (e.g. a scheduling change from another device) must not clobber the
    // move just applied.
    db.applyRemoteNode(node("a", deviceSeq = 2L))

    db.getPosition(PositionKey("a"))!!.previousAndNextMoves.nextMoves.keys shouldBe setOf("e4")
  }

  @Test
  fun getPositionIncludingDeletedReturnsATombstone() = runTest {
    val db = InMemoryDatabaseQueryManager()
    db.insertNodes(node("a"))
    db.deletePosition(PositionKey("a"), originDevice = "d", deviceSeq = 2L, updatedAt = now)

    db.getPosition(PositionKey("a")) shouldBe null
    db.getPositionIncludingDeleted(PositionKey("a"))?.isDeleted shouldBe true
  }

  @Test
  fun applyRemoteMoveWritesWithoutQueuingOutbox() = runTest {
    val db = InMemoryDatabaseQueryManager()
    db.applyRemoteNode(node("a"))
    db.applyRemoteNode(node("b"))

    db.applyRemoteMove(
      DataMove(PositionKey("a"), PositionKey("b"), "e4", isGood = true, updatedAt = now)
    )

    db.getPosition(PositionKey("a"))!!.previousAndNextMoves.nextMoves.keys shouldBe setOf("e4")
    db.getPosition(PositionKey("b"))!!.previousAndNextMoves.previousMoves.keys shouldBe setOf("e4")
    db.getOutbox() shouldBe emptyList()
  }

  @Test
  fun applyRemoteMoveWithMissingEndpointIsANoop() = runTest {
    val db = InMemoryDatabaseQueryManager()
    db.insertNodes(node("a"))

    db.applyRemoteMove(
      DataMove(PositionKey("a"), PositionKey("missing"), "e4", isGood = true, updatedAt = now)
    )

    db.getPosition(PositionKey("a"))!!.previousAndNextMoves.nextMoves shouldBe emptyMap()
  }
}
