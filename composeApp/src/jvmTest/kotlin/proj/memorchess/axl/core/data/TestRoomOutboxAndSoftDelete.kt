package proj.memorchess.axl.core.data

import androidx.room.Room
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import java.io.File
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import proj.memorchess.axl.core.graph.DeleteMode
import proj.memorchess.axl.core.graph.PreviousAndNextMoves
import proj.memorchess.axl.core.scheduling.CardStateFactory

/**
 * Room backed coverage of soft-delete stamping and the sync outbox, mirroring
 * [TestInMemoryDatabaseQueryManager]'s equivalent cases against a real isolated SQLite database.
 */
class TestRoomOutboxAndSoftDelete {

  private val database: CustomDatabase = freshDatabase()
  private val manager: DatabaseQueryManager = NonJsLocalDatabaseQueryManager(database)

  private fun freshDatabase(): CustomDatabase {
    val dbFile = File(System.getProperty("java.io.tmpdir"), "room_outbox_${UUID.randomUUID()}.db")
    return getRoomDatabase(Room.databaseBuilder<CustomDatabase>(name = dbFile.absolutePath))
  }

  @AfterTest
  fun tearDown() {
    database.close()
  }

  @Test
  fun softDeletingANodeStampsUpdatedAt() = runTest {
    val key = PositionKey("k1")
    manager.insertNodes(DataNode(key, PreviousAndNextMoves(emptyList(), emptyList()), CardStateFactory.new()))

    manager.deletePosition(
      key,
      DeleteMode.SOFT,
      originDevice = "device-a",
      deviceSeq = 7L,
      updatedAt = Instant.fromEpochSeconds(1_000),
    )

    manager.getLastUpdate() shouldBe Instant.fromEpochSeconds(1_000)
  }

  @Test
  fun reAddingASoftDeletedNodeRevivesItAsLive() = runTest {
    val key = PositionKey("k1")
    val node = DataNode(key, PreviousAndNextMoves(emptyList(), emptyList()), CardStateFactory.new())
    manager.insertNodes(node)
    manager.deletePosition(key, DeleteMode.SOFT, "device-a", 1L, Instant.fromEpochSeconds(1))

    manager.insertNodes(node)

    manager.getPosition(key).shouldNotBeNull()
  }

  @Test
  fun markDirtyThenGetOutboxRoundTripsThroughRoom() = runTest {
    val key = DirtyKey.NodeKey(PositionKey("k1"))

    manager.markDirty(key)

    manager.getOutbox() shouldBe listOf(key)
  }

  @Test
  fun reMarkingTheSameKeyDirtyDoesNotDuplicateTheOutboxRow() = runTest {
    val key = DirtyKey.EdgeKey(PositionKey("k1"), PositionKey("k2"))

    manager.markDirty(key)
    manager.markDirty(key)

    manager.getOutbox() shouldBe listOf(key)
  }

  @Test
  fun clearDirtyRemovesExactlyTheClearedKeys() = runTest {
    val kept = DirtyKey.SettingKey("appTheme")
    val cleared = DirtyKey.NodeKey(PositionKey("k1"))
    manager.markDirty(kept)
    manager.markDirty(cleared)

    manager.clearDirty(listOf(cleared))

    manager.getOutbox() shouldBe listOf(kept)
  }

  @Test
  fun outboxRoundTripsAllThreeKeyKindsThroughRoom() = runTest {
    val node = DirtyKey.NodeKey(PositionKey("k1"))
    val edge = DirtyKey.EdgeKey(PositionKey("k1"), PositionKey("k2"))
    val setting = DirtyKey.SettingKey("appTheme")

    manager.markDirty(node)
    manager.markDirty(edge)
    manager.markDirty(setting)

    manager.getOutbox() shouldContainExactlyInAnyOrder listOf(node, edge, setting)
  }
}
