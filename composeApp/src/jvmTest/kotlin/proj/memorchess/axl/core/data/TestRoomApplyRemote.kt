package proj.memorchess.axl.core.data

import androidx.room.Room
import io.kotest.matchers.shouldBe
import java.io.File
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import proj.memorchess.axl.core.graph.PreviousAndNextMoves
import proj.memorchess.axl.core.scheduling.CardState

/**
 * Room backed coverage of the sync remote-apply surface, mirroring [TestInMemoryApplyRemote]'s
 * equivalent cases against a real isolated SQLite database.
 */
class TestRoomApplyRemote {

  private val database: CustomDatabase = freshDatabase()
  private val manager: DatabaseQueryManager = NonJsLocalDatabaseQueryManager(database)
  private val now = Instant.parse("2026-01-01T00:00:00Z")

  private fun freshDatabase(): CustomDatabase {
    val dbFile =
      File(System.getProperty("java.io.tmpdir"), "room_apply_remote_${UUID.randomUUID()}.db")
    return getRoomDatabase(Room.databaseBuilder<CustomDatabase>(name = dbFile.absolutePath))
  }

  @AfterTest
  fun tearDown() {
    database.close()
  }

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

  @Test
  fun applyRemoteNodeWritesWithoutQueuingOutbox() = runTest {
    manager.applyRemoteNode(node("a"))

    manager.getPosition(PositionKey("a")) shouldBe node("a")
    manager.getOutbox() shouldBe emptyList()
  }

  @Test
  fun applyRemoteNodePreservesExistingMoves() = runTest {
    manager.insertNodes(node("a"), node("b"))
    manager.applyRemoteMove(
      DataMove(PositionKey("a"), PositionKey("b"), "e4", isGood = true, updatedAt = now)
    )

    manager.applyRemoteNode(node("a", deviceSeq = 2L))

    manager.getPosition(PositionKey("a"))!!.previousAndNextMoves.nextMoves.keys shouldBe setOf("e4")
  }

  @Test
  fun getPositionIncludingDeletedReturnsATombstone() = runTest {
    manager.insertNodes(node("a"))
    manager.deletePosition(PositionKey("a"), originDevice = "d", deviceSeq = 2L, updatedAt = now)

    manager.getPosition(PositionKey("a")) shouldBe null
    manager.getPositionIncludingDeleted(PositionKey("a"))?.isDeleted shouldBe true
  }

  @Test
  fun applyRemoteMoveWritesWithoutQueuingOutbox() = runTest {
    manager.applyRemoteNode(node("a"))
    manager.applyRemoteNode(node("b"))

    manager.applyRemoteMove(
      DataMove(PositionKey("a"), PositionKey("b"), "e4", isGood = true, updatedAt = now)
    )

    manager.getPosition(PositionKey("a"))!!.previousAndNextMoves.nextMoves.keys shouldBe setOf("e4")
    manager.getPosition(PositionKey("b"))!!.previousAndNextMoves.previousMoves.keys shouldBe
      setOf("e4")
    manager.getOutbox() shouldBe emptyList()
  }
}
