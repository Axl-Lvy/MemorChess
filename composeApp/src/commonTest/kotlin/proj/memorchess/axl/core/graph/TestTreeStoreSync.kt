package proj.memorchess.axl.core.graph

import proj.memorchess.axl.core.sync.DeviceId
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import proj.memorchess.axl.core.data.DirtyKey
import proj.memorchess.axl.core.data.InMemoryDatabaseQueryManager
import proj.memorchess.axl.core.data.PositionKey
import proj.memorchess.axl.core.scheduling.CardStateFactory
import proj.memorchess.axl.core.sync.DeviceIdentity
import proj.memorchess.axl.core.sync.toEdgeSyncRow
import proj.memorchess.axl.test_util.testRepertoireTagStore
import proj.memorchess.axl.test_util.testTreeStore

/** Tests that every [TreeStore] write marks the outbox dirty and stamps its [DeviceIdentity]. */
class TestTreeStoreSync {

  @OptIn(ExperimentalCoroutinesApi::class)
  private fun newStore(
    database: InMemoryDatabaseQueryManager,
    identity: DeviceIdentity = DeviceIdentity.ephemeral(),
  ) = testTreeStore(database, CoroutineScope(UnconfinedTestDispatcher()), identity)

  @Test
  fun addMoveMarksBothEndpointsAndTheEdgeDirty() = runTest {
    val database = InMemoryDatabaseQueryManager()
    val store = newStore(database)
    store.ensurePosition(PositionKey("start"), 0)

    store.addMove(PositionKey("start"), "e4", PositionKey("k2"), isGood = true, fromDepth = 0)

    val outbox = database.getOutbox().map { it.key }.toSet()
    assertTrue(outbox.contains(DirtyKey.NodeKey(PositionKey("start"))))
    assertTrue(outbox.contains(DirtyKey.NodeKey(PositionKey("k2"))))
    assertTrue(outbox.contains(DirtyKey.EdgeKey(PositionKey("start"), PositionKey("k2"))))
  }

  @Test
  fun installQueuesNoEdgeEntry() = runTest {
    val database = InMemoryDatabaseQueryManager()
    val store = newStore(database)
    val start = PositionKey("start")
    val k2 = PositionKey("k2")

    store.install("a", listOf(InstalledMove(start, "e4", k2, isGood = true, fromDepth = 0)))

    val outbox = database.getOutbox().map { it.key }.toSet()
    assertTrue(outbox.contains(DirtyKey.TagKey(start, k2, "a")))
    assertTrue(outbox.contains(DirtyKey.NodeKey(start)))
    assertTrue(outbox.contains(DirtyKey.NodeKey(k2)))
    assertFalse(outbox.any { it is DirtyKey.EdgeKey })
  }

  @Test
  fun deleteMoveQueuesTheManualHoldTombstoneAndEveryTagTombstone() = runTest {
    val database = InMemoryDatabaseQueryManager()
    val store = newStore(database)
    val tagStore = testRepertoireTagStore(database)
    val start = PositionKey("start")
    val k2 = PositionKey("k2")
    store.addMove(start, "e4", k2, isGood = true, fromDepth = 0)
    store.install("installed", listOf(InstalledMove(start, "e4", k2, isGood = true, fromDepth = 0)))
    tagStore.tag(start, k2, "own")
    for (entry in database.getOutbox()) database.clearDirty(listOf(entry))

    store.deleteMove(start, "e4")

    val outbox = database.getOutbox().map { it.key }.toSet()
    assertTrue(outbox.contains(DirtyKey.EdgeKey(start, k2)))
    assertTrue(outbox.contains(DirtyKey.TagKey(start, k2, "installed")))
    assertTrue(outbox.contains(DirtyKey.TagKey(start, k2, "own")))
    val tombstone =
      database.getPositionIncludingDeleted(start)!!.previousAndNextMoves.nextMoves.getValue("e4")
    assertTrue(tombstone.toEdgeSyncRow()!!.isDeleted)
    assertTrue(database.getTags(start, k2).isEmpty())
  }

  @Test
  fun addMoveStampsThePersistedNodeWithTheStoresDeviceIdentity() = runTest {
    val database = InMemoryDatabaseQueryManager()
    val identity = DeviceIdentity.ephemeral()
    val store = newStore(database, identity)
    store.ensurePosition(PositionKey("start"), 0)

    store.addMove(PositionKey("start"), "e4", PositionKey("k2"), isGood = true, fromDepth = 0)

    val persisted = database.getPosition(PositionKey("start"))!!
    assertTrue(persisted.originDevice == identity.originDevice.value)
    assertTrue(persisted.deviceSeq > 0L)
  }

  @Test
  fun addMovesMarksEveryTouchedNodeAndEdgeDirty() = runTest {
    val database = InMemoryDatabaseQueryManager()
    val store = newStore(database)
    store.ensurePosition(PositionKey("start"), 0)

    store.addMoves(
      listOf(
        MoveInsertion(PositionKey("start"), "e4", PositionKey("k2"), isGood = true, fromDepth = 0),
        MoveInsertion(PositionKey("k2"), "e5", PositionKey("k3"), isGood = true, fromDepth = 1),
      )
    )

    val outbox = database.getOutbox().map { it.key }.toSet()
    assertTrue(outbox.contains(DirtyKey.NodeKey(PositionKey("start"))))
    assertTrue(outbox.contains(DirtyKey.NodeKey(PositionKey("k2"))))
    assertTrue(outbox.contains(DirtyKey.NodeKey(PositionKey("k3"))))
    assertTrue(outbox.contains(DirtyKey.EdgeKey(PositionKey("start"), PositionKey("k2"))))
    assertTrue(outbox.contains(DirtyKey.EdgeKey(PositionKey("k2"), PositionKey("k3"))))
  }

  @Test
  fun deleteNodeDefaultsToSoftDeleteAndMarksTheNodeAndItsIncidentEdgesDirty() = runTest {
    val database = InMemoryDatabaseQueryManager()
    val store = newStore(database)
    store.ensurePosition(PositionKey("start"), 0)
    store.addMove(PositionKey("start"), "e4", PositionKey("k2"), isGood = true, fromDepth = 0)

    store.deleteNode(PositionKey("k2"))

    val outbox = database.getOutbox().map { it.key }.toSet()
    assertTrue(outbox.contains(DirtyKey.NodeKey(PositionKey("k2"))))
    assertTrue(outbox.contains(DirtyKey.EdgeKey(PositionKey("start"), PositionKey("k2"))))
    assertTrue(outbox.contains(DirtyKey.NodeKey(PositionKey("start"))))
  }

  @Test
  fun deleteMoveMarksTheEdgeAndTheSurvivingOriginDirty() = runTest {
    val database = InMemoryDatabaseQueryManager()
    val store = newStore(database)
    store.ensurePosition(PositionKey("start"), 0)
    store.addMove(PositionKey("start"), "e4", PositionKey("k2"), isGood = true, fromDepth = 0)

    store.deleteMove(PositionKey("start"), "e4")

    val outbox = database.getOutbox().map { it.key }.toSet()
    assertTrue(outbox.contains(DirtyKey.EdgeKey(PositionKey("start"), PositionKey("k2"))))
    assertTrue(outbox.contains(DirtyKey.NodeKey(PositionKey("start"))))
  }

  @Test
  fun deleteMoveTombstoneSurvivesTheFollowUpPersistOfTheSurvivingOrigin() = runTest {
    val database = InMemoryDatabaseQueryManager()
    val store = newStore(database)
    store.ensurePosition(PositionKey("start"), 0)
    store.addMove(PositionKey("start"), "e4", PositionKey("k2"), isGood = true, fromDepth = 0)

    store.deleteMove(PositionKey("start"), "e4")

    val raw = database.getPosition(PositionKey("start"))!!
    assertTrue(raw.previousAndNextMoves.nextMoves.getValue("e4").isDeleted)
  }

  @Test
  fun deleteMoveThroughAColdCacheStillUpdatesHasGoodOutgoingAndQueuesTheNode() = runTest {
    // A second store over the same database starts with a cold cache.
    val database = InMemoryDatabaseQueryManager()
    val store1 = newStore(database)
    store1.ensurePosition(PositionKey("start"), 0)
    store1.addMove(PositionKey("start"), "e4", PositionKey("k2"), isGood = true, fromDepth = 0)

    val store2 = newStore(database)
    store2.deleteMove(PositionKey("start"), "e4")

    val raw = database.getPosition(PositionKey("start"))!!
    assertFalse(raw.hasGoodOutgoing)
    assertTrue(database.getOutbox().map { it.key }.contains(DirtyKey.NodeKey(PositionKey("start"))))
  }

  @Test
  fun updateCardStateMarksTheNodeDirty() = runTest {
    val database = InMemoryDatabaseQueryManager()
    val store = newStore(database)
    store.ensurePosition(PositionKey("start"), 0)
    store.addMove(PositionKey("start"), "e4", PositionKey("k2"), isGood = true, fromDepth = 0)

    store.updateCardState(PositionKey("k2"), CardStateFactory.new())

    assertTrue(database.getOutbox().map { it.key }.contains(DirtyKey.NodeKey(PositionKey("k2"))))
  }

  @Test
  fun reAddingAMoveThroughTreeStoreAfterDeleteMoveIsVisibleAgain() = runTest {
    val database = InMemoryDatabaseQueryManager()
    val store = newStore(database)
    store.ensurePosition(PositionKey("start"), 0)
    store.addMove(PositionKey("start"), "e4", PositionKey("k2"), isGood = true, fromDepth = 0)
    store.deleteMove(PositionKey("start"), "e4")

    store.addMove(PositionKey("start"), "e4", PositionKey("k2"), isGood = true, fromDepth = 0)

    val node = store.node(PositionKey("start"))!!
    assertNotNull(node.outgoing["e4"])
  }
}
