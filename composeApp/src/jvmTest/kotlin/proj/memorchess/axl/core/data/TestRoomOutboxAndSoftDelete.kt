package proj.memorchess.axl.core.data

import androidx.room.Room
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.maps.shouldBeEmpty
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
    manager.insertNodes(
      DataNode(key, PreviousAndNextMoves(emptyList(), emptyList()), CardStateFactory.new())
    )

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

    manager.markDirty(key, 1L)

    manager.getOutbox() shouldBe listOf(OutboxEntry(key, 1L))
  }

  @Test
  fun reMarkingTheSameKeyDirtyKeepsTheHigherSequenceAndDoesNotDuplicateTheRow() = runTest {
    val key = DirtyKey.EdgeKey(PositionKey("k1"), PositionKey("k2"))

    manager.markDirty(key, 1L)
    manager.markDirty(key, 2L)

    manager.getOutbox() shouldBe listOf(OutboxEntry(key, 2L))
  }

  @Test
  fun clearDirtyRemovesExactlyTheClearedKeys() = runTest {
    val kept = DirtyKey.SettingKey("appTheme")
    val cleared = DirtyKey.NodeKey(PositionKey("k1"))
    manager.markDirty(kept, 1L)
    manager.markDirty(cleared, 1L)

    manager.clearDirty(listOf(OutboxEntry(cleared, 1L)))

    manager.getOutbox() shouldBe listOf(OutboxEntry(kept, 1L))
  }

  @Test
  fun clearDirtySurvivesAMarkThatLandsAfterTheEntryWasRead() = runTest {
    val key = DirtyKey.NodeKey(PositionKey("k1"))
    manager.markDirty(key, 1L)
    val read = manager.getOutbox()

    manager.markDirty(key, 2L)
    manager.clearDirty(read)

    manager.getOutbox() shouldBe listOf(OutboxEntry(key, 2L))
  }

  @Test
  fun outboxRoundTripsAllFiveKeyKindsThroughRoom() = runTest {
    val node = DirtyKey.NodeKey(PositionKey("k1"))
    val edge = DirtyKey.EdgeKey(PositionKey("k1"), PositionKey("k2"))
    val setting = DirtyKey.SettingKey("appTheme")
    val repertoire = DirtyKey.RepertoireKey("italian-game")
    val tag = DirtyKey.TagKey(PositionKey("k1"), PositionKey("k2"), "italian-game")

    manager.markDirty(node, 1L)
    manager.markDirty(edge, 1L)
    manager.markDirty(setting, 1L)
    manager.markDirty(repertoire, 1L)
    manager.markDirty(tag, 1L)

    manager.getOutbox().map { it.key } shouldContainExactlyInAnyOrder
      listOf(node, edge, setting, repertoire, tag)
  }

  @Test
  fun insertRepertoireThenGetRepertoireRoundTripsThroughRoom() = runTest {
    // Room's DateConverters store an Instant at second precision, so updatedAt must already be
    // whole seconds for a byte-identical round trip, exactly like TestSyncStorePush's own
    // sub-millisecond-precision test documents server side.
    val repertoire =
      DataRepertoire(
        id = "italian-game",
        name = "Italian Game",
        color = null,
        updatedAt = Instant.fromEpochSeconds(1_000),
      )

    manager.insertRepertoire(repertoire)

    manager.getRepertoire("italian-game") shouldBe repertoire
    manager.getOutbox().map { it.key } shouldBe listOf(DirtyKey.RepertoireKey("italian-game"))
  }

  @Test
  fun insertTagThenGetTagsRoundTripsThroughRoom() = runTest {
    val origin = PositionKey("k1")
    val destination = PositionKey("k2")
    val tag =
      DataEdgeRepertoireTag(
        origin,
        destination,
        repertoireId = "italian-game",
        updatedAt = Instant.fromEpochSeconds(1_000),
      )

    manager.insertTag(tag)

    manager.getTags(origin, destination) shouldBe listOf(tag)
    manager.getOutbox().map { it.key } shouldBe
      listOf(DirtyKey.TagKey(origin, destination, "italian-game"))
  }

  @Test
  fun softDeletingAPositionQueuesTheNodeAndIncidentEdgesInTheSameTransaction() = runTest {
    val origin = PositionKey("k1")
    val destination = PositionKey("k2")
    val move = DataMove(origin, destination, "e4", isGood = true)
    manager.insertNodes(
      DataNode(origin, PreviousAndNextMoves(emptyList(), listOf(move)), CardStateFactory.new()),
      DataNode(
        destination,
        PreviousAndNextMoves(listOf(move), emptyList()),
        CardStateFactory.new(),
      ),
    )
    // The insertNodes calls above already queued both nodes; clear that so this assertion is
    // specific to what deletePosition itself queues.
    manager.clearDirty(manager.getOutbox())

    manager.deletePosition(origin, DeleteMode.SOFT, "device-a", 9L, Instant.fromEpochSeconds(1))

    manager.getOutbox() shouldContainExactlyInAnyOrder
      listOf(
        OutboxEntry(DirtyKey.NodeKey(origin), 9L),
        OutboxEntry(DirtyKey.EdgeKey(origin, destination), 9L),
      )
    // The cascade reached the MoveEntity row itself through the public API, not only the outbox.
    manager.getPosition(destination)!!.previousAndNextMoves.previousMoves.shouldBeEmpty()
  }

  @Test
  fun softDeletingAMoveQueuesItsEdgeInTheSameTransaction() = runTest {
    val origin = PositionKey("k1")
    val destination = PositionKey("k2")
    val move = DataMove(origin, destination, "e4", isGood = true)
    manager.insertNodes(
      DataNode(origin, PreviousAndNextMoves(emptyList(), listOf(move)), CardStateFactory.new()),
      DataNode(
        destination,
        PreviousAndNextMoves(listOf(move), emptyList()),
        CardStateFactory.new(),
      ),
    )
    manager.clearDirty(manager.getOutbox())

    manager.deleteMove(origin, "e4", DeleteMode.SOFT, "device-a", 3L, Instant.fromEpochSeconds(1))

    manager.getOutbox() shouldBe listOf(OutboxEntry(DirtyKey.EdgeKey(origin, destination), 3L))
  }

  @Test
  fun eraseAllClearsTheOutbox() = runTest {
    manager.markDirty(DirtyKey.NodeKey(PositionKey("k1")), 1L)

    manager.eraseAll()

    manager.getOutbox() shouldBe emptyList()
  }

  @Test
  fun eraseAllClearsRepertoiresAndTags() = runTest {
    val origin = PositionKey("k1")
    val destination = PositionKey("k2")
    manager.insertRepertoire(DataRepertoire(id = "italian-game", name = "Italian Game", color = null))
    manager.insertTag(DataEdgeRepertoireTag(origin, destination, repertoireId = "italian-game"))

    manager.eraseAll()

    manager.getRepertoire("italian-game") shouldBe null
    manager.getTags(origin, destination).shouldBeEmpty()
  }
}
