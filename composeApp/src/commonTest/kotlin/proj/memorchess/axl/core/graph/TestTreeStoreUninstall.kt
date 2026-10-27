package proj.memorchess.axl.core.graph

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlin.test.Test
import kotlinx.coroutines.test.runTest
import proj.memorchess.axl.core.data.DataMove
import proj.memorchess.axl.core.data.DirtyKey
import proj.memorchess.axl.core.data.InMemoryDatabaseQueryManager
import proj.memorchess.axl.core.data.PositionKey
import proj.memorchess.axl.core.data.TaggedEdge
import proj.memorchess.axl.core.scheduling.CardStateFactory
import proj.memorchess.axl.core.sync.toEdgeSyncRow
import proj.memorchess.axl.test_util.TestDatabases
import proj.memorchess.axl.test_util.testRepertoireTagStore
import proj.memorchess.axl.test_util.testTreeStore

/** Uninstalling, reinstalling and updating an installed repertoire through [TreeStore]. */
class TestTreeStoreUninstall {

  private val start = PositionKey.START_POSITION
  private val posA = PositionKey("posA b K")
  private val posB = PositionKey("posB b K")
  private val posC = PositionKey("posC w K")
  private val posD = PositionKey("posD b K")

  private val e4 = TaggedEdge(start, posB, "e4")
  private val d4 = TaggedEdge(start, posA, "d4")
  private val nf3 = TaggedEdge(posA, posB, "Nf3")
  private val e5 = TaggedEdge(posB, posC, "e5")
  private val f5 = TaggedEdge(posC, posD, "f5")

  private fun TaggedEdge.installed(isGood: Boolean, fromDepth: Int) =
    InstalledMove(origin, move, destination, isGood, fromDepth)

  private suspend fun InMemoryDatabaseQueryManager.move(edge: TaggedEdge): DataMove? =
    getPositionIncludingDeleted(edge.origin)?.previousAndNextMoves?.nextMoves?.get(edge.move)

  private suspend fun InMemoryDatabaseQueryManager.isLive(edge: TaggedEdge): Boolean =
    move(edge)?.isDeleted == false

  private suspend fun installed(
    database: InMemoryDatabaseQueryManager,
    store: TreeStore,
    id: String,
    vararg moves: InstalledMove,
  ) {
    testRepertoireTagStore(database).register(id, id, color = null, installedVersion = 1)
    store.install(id, moves.toList())
  }

  @Test
  fun uninstallDeletesTheMovesOnlyTheRepertoireHeld() = runTest {
    val database = TestDatabases.empty()
    val store = testTreeStore(database)
    installed(database, store, "x", e4.installed(true, 0))

    store.uninstall("x")

    store.node(start)!!.outgoing["e4"] shouldBe null
    database.isLive(e4) shouldBe false
    database.getPositionIncludingDeleted(posB)!!.isDeleted shouldBe true
  }

  @Test
  fun uninstallKeepsAManuallyHeldMoveWithItsOwnMarking() = runTest {
    val database = TestDatabases.empty()
    val store = testTreeStore(database)
    store.addMove(start, "e4", posB, isGood = false, fromDepth = 0)
    installed(database, store, "x", e4.installed(true, 0))

    store.uninstall("x")

    val move = database.move(e4)!!
    move.isDeleted shouldBe false
    move.isGood shouldBe false
    move.ownIsGood shouldBe false
  }

  @Test
  fun uninstallTurnsAMoveAnotherInstallMarksBadToBad() = runTest {
    val database = TestDatabases.empty()
    val store = testTreeStore(database)
    installed(database, store, "x", e4.installed(true, 0))
    installed(database, store, "y", e4.installed(false, 0))

    store.uninstall("x")

    database.move(e4)!!.isGood shouldBe false
    store.node(start)!!.outgoing.getValue("e4").isGood shouldBe false
  }

  @Test
  fun uninstallCascadesThroughAPositionOnlyTheRepertoireReached() = runTest {
    val database = TestDatabases.empty()
    val store = testTreeStore(database)
    installed(database, store, "x", e4.installed(true, 0), e5.installed(true, 1))
    installed(database, store, "y", d4.installed(true, 0), nf3.installed(true, 1))
    store.deleteMove(start, "e4")

    store.uninstall("y")

    database.isLive(e5) shouldBe false
    database.getPositionIncludingDeleted(posB)!!.isDeleted shouldBe true
    database.getPositionIncludingDeleted(posC)!!.isDeleted shouldBe true
  }

  @Test
  fun uninstallQueuesNoEdgeEntryForMovesNeverHeldManually() = runTest {
    val database = TestDatabases.empty()
    val store = testTreeStore(database)
    installed(database, store, "x", e4.installed(true, 0), e5.installed(true, 1))
    database.clearDirty(database.getOutbox())

    store.uninstall("x")

    val keys = database.getOutbox().map { it.key }
    keys.filterIsInstance<DirtyKey.EdgeKey>() shouldBe emptyList()
    val deletedCopy =
      database
        .getPositionIncludingDeleted(posB)!!
        .previousAndNextMoves
        .previousMoves
        .values
        .single()
    deletedCopy.toEdgeSyncRow() shouldBe null
    keys shouldContainAll
      listOf(
        DirtyKey.RepertoireKey("x"),
        DirtyKey.TagKey(start, posB, "x"),
        DirtyKey.TagKey(posB, posC, "x"),
        DirtyKey.NodeKey(posB),
        DirtyKey.NodeKey(posC),
      )
  }

  @Test
  fun uninstallTombstonesTheRegistryRow() = runTest {
    val database = TestDatabases.empty()
    val store = testTreeStore(database)
    installed(database, store, "x", e4.installed(true, 0))

    store.uninstall("x")

    testRepertoireTagStore(database).repertoire("x") shouldBe null
    database.getRepertoireIncludingDeleted("x")!!.isDeleted shouldBe true
  }

  @Test
  fun uninstallClearsHasGoodOutgoingOfAnOriginLeftWithoutGoodMoves() = runTest {
    val database = TestDatabases.empty()
    val store = testTreeStore(database)
    store.addMove(start, "d4", posA, isGood = false, fromDepth = 0)
    installed(database, store, "x", e4.installed(true, 0))

    store.uninstall("x")

    database.getPosition(start)!!.hasGoodOutgoing shouldBe false
  }

  @Test
  fun previewUninstallCountsWhatUninstallDeletes() = runTest {
    val database = TestDatabases.empty()
    val store = testTreeStore(database)
    store.addMove(start, "d4", posA, isGood = true, fromDepth = 0)
    installed(
      database,
      store,
      "x",
      d4.installed(true, 0),
      e4.installed(true, 0),
      e5.installed(true, 1),
      f5.installed(true, 2),
    )

    val preview = store.previewUninstall("x")
    store.uninstall("x")

    preview shouldBe 3
    listOf(e4, e5, f5).count { !database.isLive(it) } shouldBe 3
    database.isLive(d4) shouldBe true
  }

  @Test
  fun reinstallAfterUninstallStartsTheMovesFromScratch() = runTest {
    val database = TestDatabases.empty()
    val store = testTreeStore(database)
    installed(database, store, "x", e4.installed(true, 0))
    store.updateCardState(posB, CardStateFactory.new().copy(reps = 3))
    store.uninstall("x")

    installed(database, store, "x", e4.installed(true, 0))

    store.node(posB)!!.cardState.reps shouldBe 0
    database.isLive(e4) shouldBe true
  }

  @Test
  fun uninstallOfAnOwnRepertoireIsRefused() = runTest {
    val database = TestDatabases.empty()
    val store = testTreeStore(database)
    testRepertoireTagStore(database).register("own", "Own", color = null)

    shouldThrow<IllegalArgumentException> { store.uninstall("own") }
    shouldThrow<IllegalArgumentException> { store.uninstall("missing") }
  }

  @Test
  fun uninstallLeavesTheOtherRepertoiresTagsAlone() = runTest {
    val database = TestDatabases.empty()
    val store = testTreeStore(database)
    installed(database, store, "x", e4.installed(true, 0))
    installed(database, store, "y", e4.installed(true, 0))

    store.uninstall("x")

    database.getTags(start, posB).map { it.repertoireId } shouldBe listOf("y")
    database.isLive(e4) shouldBe true
  }

  @Test
  fun anUpdateDeletesTheMovesTheNewVersionDropped() = runTest {
    val database = TestDatabases.empty()
    val store = testTreeStore(database)
    installed(database, store, "x", e4.installed(true, 0), e5.installed(true, 1))

    store.install("x", listOf(e4.installed(true, 0)))

    database.isLive(e4) shouldBe true
    database.isLive(e5) shouldBe false
    database.getPositionIncludingDeleted(posC)!!.isDeleted shouldBe true
    database.getTags(posB, posC) shouldBe emptyList()
  }

  @Test
  fun anUpdateGivesKeptMovesTheNewMarkingUnlessTheUserMarkedThem() = runTest {
    val database = TestDatabases.empty()
    val store = testTreeStore(database)
    store.addMove(start, "d4", posA, isGood = true, fromDepth = 0)
    installed(database, store, "x", e4.installed(true, 0), d4.installed(true, 0))

    store.install("x", listOf(e4.installed(false, 0), d4.installed(false, 0)))

    database.move(e4)!!.isGood shouldBe false
    database.move(d4)!!.isGood shouldBe true
  }

  @Test
  fun anUpdateKeepsAPositionTheNewVersionReachesThroughANewMove() = runTest {
    val database = TestDatabases.empty()
    val store = testTreeStore(database)
    installed(database, store, "x", e4.installed(true, 0), e5.installed(true, 1))
    val v2 = listOf(d4.installed(true, 0), nf3.installed(true, 1), e5.installed(true, 1))

    val preview = store.previewInstall("x", v2.map { TaggedEdge(it.from, it.to, it.move) })
    store.install("x", v2)

    preview shouldBe 1
    database.isLive(e4) shouldBe false
    database.isLive(e5) shouldBe true
    database.getPosition(posB) shouldNotBe null
  }

  @Test
  fun aSameVersionReinstallRestoresADeletedMoveAndDeletesNothing() = runTest {
    val database = TestDatabases.empty()
    val store = testTreeStore(database)
    val v1 = listOf(e4.installed(true, 0), e5.installed(true, 1))
    installed(database, store, "x", *v1.toTypedArray())
    store.deleteMove(posB, "e5")

    val preview = store.previewInstall("x", v1.map { TaggedEdge(it.from, it.to, it.move) })
    store.install("x", v1)

    preview shouldBe 0
    database.isLive(e4) shouldBe true
    database.isLive(e5) shouldBe true
  }
}
