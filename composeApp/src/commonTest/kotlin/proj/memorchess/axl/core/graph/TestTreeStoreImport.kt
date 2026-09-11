package proj.memorchess.axl.core.graph

import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlin.test.Test
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import proj.memorchess.axl.core.data.DataMove
import proj.memorchess.axl.core.data.DataNode
import proj.memorchess.axl.core.data.DirtyKey
import proj.memorchess.axl.core.data.PositionKey
import proj.memorchess.axl.core.scheduling.CardStateFactory
import proj.memorchess.axl.core.sync.resolve
import proj.memorchess.axl.core.sync.toNodeSyncRow
import proj.memorchess.axl.test_util.TestDatabases
import proj.memorchess.axl.test_util.testTreeStore

/**
 * Tests for [TreeStore.importNodes], the entry point [GraphSerializer.deserialize] results must be
 * persisted through instead of a direct [proj.memorchess.axl.core.data.DatabaseQueryManager] call,
 * since the wire format carries no device identity of its own.
 */
class TestTreeStoreImport {

  private val start = PositionKey.START_POSITION
  private val posA = PositionKey("posA b K")

  /**
   * A two node graph connected by one edge, shaped exactly as [GraphSerializer.deserialize] emits
   * it: the same [DataMove] appears once in [start]'s `nextMoves` and once in [posA]'s
   * `previousMoves`, both with the wire format's placeholder `""`/`0L` identity.
   */
  private fun importedGraph(updatedAt: Instant): List<DataNode> {
    val edge =
      DataMove(
        origin = start,
        destination = posA,
        move = "e4",
        isGood = true,
        updatedAt = updatedAt,
      )
    return listOf(
      DataNode(
        positionKey = start,
        previousAndNextMoves =
          PreviousAndNextMoves(previousMoves = listOf(), nextMoves = listOf(edge)),
        cardState = CardStateFactory.new(),
        depth = 0,
        updatedAt = updatedAt,
      ),
      DataNode(
        positionKey = posA,
        previousAndNextMoves =
          PreviousAndNextMoves(previousMoves = listOf(edge), nextMoves = listOf()),
        cardState = CardStateFactory.new(),
        depth = 1,
        updatedAt = updatedAt,
      ),
    )
  }

  @Test
  fun importNodesStampsEveryNodeAndEdgeWithARealDeviceIdentity() = runTest {
    val database = TestDatabases.empty()
    val store = testTreeStore(database)

    store.importNodes(importedGraph(Instant.parse("2026-01-01T00:00:00Z")))

    val origin = database.getPosition(start)!!
    val destination = database.getPosition(posA)!!
    origin.originDevice shouldNotBe ""
    origin.deviceSeq shouldBeGreaterThan 0L
    destination.originDevice shouldBe origin.originDevice

    // The trap: the edge is described once from each endpoint. Both copies must carry the exact
    // same stamp, not two different ones from a naive allocation per node.
    val fromOrigin = origin.previousAndNextMoves.nextMoves.getValue("e4")
    val fromDestination = destination.previousAndNextMoves.previousMoves.getValue("e4")
    fromOrigin.originDevice shouldBe origin.originDevice
    fromOrigin.deviceSeq shouldBeGreaterThan 0L
    fromDestination.deviceSeq shouldBe fromOrigin.deviceSeq
    fromDestination.originDevice shouldBe fromOrigin.originDevice
  }

  @Test
  fun importNodesQueuesTheImportedEdgeForSync() = runTest {
    val database = TestDatabases.empty()
    val store = testTreeStore(database)

    store.importNodes(importedGraph(Instant.parse("2026-01-01T00:00:00Z")))

    database.getOutbox().map { it.key } shouldContain DirtyKey.EdgeKey(start, posA)
  }

  @Test
  fun importNodesOutboxEntriesSortAfterThisDevicesEarlierRealWrites() = runTest {
    val database = TestDatabases.empty()
    val store = testTreeStore(database)
    val posD = PositionKey("posD b K")
    store.addMove(from = start, move = "d4", to = posD, isGood = true, fromDepth = 0)
    val beforeImport = database.getOutbox().maxOf { it.deviceSeq }

    store.importNodes(importedGraph(Instant.parse("2026-01-01T00:00:00Z")))

    val afterImport = database.getOutbox().associateBy { it.key }
    afterImport.getValue(DirtyKey.NodeKey(posA)).deviceSeq shouldBeGreaterThan beforeImport
    afterImport.getValue(DirtyKey.EdgeKey(start, posA)).deviceSeq shouldBeGreaterThan beforeImport
  }

  @Test
  fun twoDevicesImportingDifferentContentForTheSamePositionConverge() = runTest {
    val older = Instant.parse("2026-01-01T00:00:00Z")
    val newer = Instant.parse("2026-01-02T00:00:00Z")

    fun singleNodeGraph(updatedAt: Instant, depth: Int) =
      listOf(
        DataNode(
          positionKey = start,
          previousAndNextMoves = PreviousAndNextMoves(),
          cardState = CardStateFactory.new(),
          depth = depth,
          updatedAt = updatedAt,
        )
      )

    val databaseA = TestDatabases.empty()
    testTreeStore(databaseA).importNodes(singleNodeGraph(older, depth = 0))
    val databaseB = TestDatabases.empty()
    testTreeStore(databaseB).importNodes(singleNodeGraph(newer, depth = 5))

    val nodeA = databaseA.getPosition(start)!!
    val nodeB = databaseB.getPosition(start)!!

    // Two independent devices produce two different identities. Before the fix both stayed the
    // wire format's shared "" placeholder and could never be told apart.
    nodeA.originDevice shouldNotBe nodeB.originDevice

    // Newer content must win the same way seen from either side, which is what makes the two
    // devices converge instead of each insisting its own copy is authoritative forever.
    val resolutionFromA = resolve(nodeA.toNodeSyncRow(), nodeB.toNodeSyncRow())
    val resolutionFromB = resolve(nodeB.toNodeSyncRow(), nodeA.toNodeSyncRow())
    resolutionFromA.row.originDevice shouldBe nodeB.originDevice
    resolutionFromB.row.originDevice shouldBe nodeB.originDevice
  }
}
