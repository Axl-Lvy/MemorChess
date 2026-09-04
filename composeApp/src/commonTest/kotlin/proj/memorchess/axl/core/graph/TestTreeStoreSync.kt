package proj.memorchess.axl.core.graph

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

/**
 * Behavioural tests proving [TreeStore] marks the outbox dirty and stamps its [DeviceIdentity] on
 * every write path: [TreeStore.addMove], [TreeStore.addMoves], [TreeStore.updateCardState],
 * [TreeStore.deleteMove] and [TreeStore.deleteNode].
 */
class TestTreeStoreSync {

  @OptIn(ExperimentalCoroutinesApi::class)
  private fun newStore(
    database: InMemoryDatabaseQueryManager,
    identity: DeviceIdentity = DeviceIdentity.ephemeral(),
  ) = TreeStore(database, CoroutineScope(UnconfinedTestDispatcher()), identity)

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
  fun addMoveStampsThePersistedNodeWithTheStoresDeviceIdentity() = runTest {
    val database = InMemoryDatabaseQueryManager()
    val identity = DeviceIdentity.ephemeral()
    val store = newStore(database, identity)
    store.ensurePosition(PositionKey("start"), 0)

    store.addMove(PositionKey("start"), "e4", PositionKey("k2"), isGood = true, fromDepth = 0)

    val persisted = database.getPosition(PositionKey("start"))!!
    assertTrue(persisted.originDevice == identity.originDevice)
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
    // Regression coverage: deleteMove's persistNode(from) call, driven by a cache that has already
    // dropped the removed edge, must not erase the tombstone deleteMove itself just wrote.
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
    // Regression coverage: a second TreeStore over the same backing database starts with an empty
    // cache, so "start" is not resident. deleteMove must resolve it through node() itself rather
    // than relying on the caller having warmed it, or the follow up persistNode(from) is a
    // documented no-op on a cache miss and hasGoodOutgoing silently goes stale.
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
