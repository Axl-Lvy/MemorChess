package proj.memorchess.axl.core.data

import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import proj.memorchess.axl.core.graph.PreviousAndNextMoves
import proj.memorchess.axl.core.scheduling.CardState

/** IndexedDB backed coverage of the sync remote-apply surface. */
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
    db.getPosition(PositionKey("b"))!!.previousAndNextMoves.previousMoves.keys shouldBe setOf("e4")
    db.getOutbox() shouldBe emptyList()
  }

  @Test
  fun edgesTaggedWithJoinsTheTagOntoItsMoveThroughIndexedDb() = runTest {
    val db = manager()
    db.eraseAll()
    val origin = PositionKey("a")
    val destination = PositionKey("b")
    db.applyRemoteNode(node("a"))
    db.applyRemoteNode(node("b"))
    db.applyRemoteMove(DataMove(origin, destination, "e4", isGood = true, updatedAt = now))
    db.insertTag(
      DataEdgeRepertoireTag(origin, destination, repertoireId = "italian-game", move = "e4")
    )

    db.edgesTaggedWith("italian-game") shouldBe listOf(TaggedEdge(origin, destination, "e4"))
  }

  @Test
  fun edgesTaggedWithExcludesADeletedTagThroughIndexedDb() = runTest {
    val db = manager()
    db.eraseAll()
    val origin = PositionKey("a")
    val destination = PositionKey("b")
    db.applyRemoteNode(node("a"))
    db.applyRemoteNode(node("b"))
    db.applyRemoteMove(DataMove(origin, destination, "e4", isGood = true, updatedAt = now))
    db.insertTag(
      DataEdgeRepertoireTag(
        origin,
        destination,
        repertoireId = "italian-game",
        move = "e4",
        isDeleted = true,
      )
    )

    db.edgesTaggedWith("italian-game") shouldBe emptyList()
  }

  @Test
  fun aMoveKeepsItsOwnAndEffectiveMarkings() = runTest {
    val db = manager()
    db.eraseAll()
    db.applyRemoteNode(node("a"))
    db.applyRemoteNode(node("b"))
    val move =
      DataMove(
        PositionKey("a"),
        PositionKey("b"),
        "e4",
        isGood = true,
        ownIsGood = null,
        updatedAt = now,
      )

    db.applyRemoteMove(move)

    val stored = db.getPosition(PositionKey("a"))!!.previousAndNextMoves.nextMoves.getValue("e4")
    stored.isGood shouldBe true
    stored.ownIsGood shouldBe null
  }

  @Test
  fun aSoftDeletedMoveLosesItsManualHold() = runTest {
    val db = manager()
    db.eraseAll()
    db.applyRemoteNode(node("a"))
    db.applyRemoteNode(node("b"))
    db.applyRemoteMove(
      DataMove(
        PositionKey("a"),
        PositionKey("b"),
        "e4",
        isGood = true,
        ownIsGood = true,
        updatedAt = now,
      )
    )

    db.deleteMove(PositionKey("a"), "e4", "d", 2L, now)

    val stored =
      db
        .getPositionIncludingDeleted(PositionKey("a"))!!
        .previousAndNextMoves
        .nextMoves
        .getValue("e4")
    stored.isDeleted shouldBe true
    stored.ownIsGood shouldBe null
  }

  @Test
  fun aTagKeepsItsMoveAndMarking() = runTest {
    val db = manager()
    db.eraseAll()
    val tag =
      DataEdgeRepertoireTag(
        PositionKey("a"),
        PositionKey("b"),
        repertoireId = "london",
        move = "d4",
        isGood = false,
        updatedAt = now,
      )

    db.applyRemoteTag(tag)

    db.getTagIncludingDeleted(PositionKey("a"), PositionKey("b"), "london") shouldBe tag
  }

  @Test
  fun aRepertoireKeepsItsInstalledVersion() = runTest {
    val db = manager()
    db.eraseAll()
    val repertoire =
      DataRepertoire(
        id = "london",
        name = "London",
        color = null,
        installedVersion = 4,
        updatedAt = now,
      )

    db.applyRemoteRepertoire(repertoire)

    db.getRepertoire("london") shouldBe repertoire
  }

  @Test
  fun getTagsTouchingReturnsLiveTagsFromOrToAPosition() = runTest {
    val db = manager()
    db.eraseAll()
    val a = PositionKey("a")
    val b = PositionKey("b")
    val c = PositionKey("c")
    db.applyRemoteTag(DataEdgeRepertoireTag(a, b, repertoireId = "r", move = "e4", updatedAt = now))
    db.applyRemoteTag(DataEdgeRepertoireTag(c, a, repertoireId = "r", move = "d4", updatedAt = now))
    db.applyRemoteTag(DataEdgeRepertoireTag(b, c, repertoireId = "r", move = "c4", updatedAt = now))
    db.applyRemoteTag(
      DataEdgeRepertoireTag(
        a,
        c,
        repertoireId = "r",
        move = "f4",
        isDeleted = true,
        updatedAt = now,
      )
    )

    db.getTagsTouching(a).map { it.move }.toSet() shouldBe setOf("e4", "d4")
  }
}
