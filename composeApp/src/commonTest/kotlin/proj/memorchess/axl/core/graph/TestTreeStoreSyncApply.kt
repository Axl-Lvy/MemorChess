package proj.memorchess.axl.core.graph

import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import proj.memorchess.axl.core.data.InMemoryDatabaseQueryManager
import proj.memorchess.axl.core.data.PositionKey
import proj.memorchess.axl.core.sync.DeviceIdentity
import proj.memorchess.axl.core.sync.EdgeSyncRow
import proj.memorchess.axl.core.sync.NodeSyncRow
import proj.memorchess.axl.core.sync.ResolutionSource

class TestTreeStoreSyncApply {

  private val now = Instant.parse("2026-01-01T00:00:00Z")

  private fun store(database: InMemoryDatabaseQueryManager = InMemoryDatabaseQueryManager()) =
    TreeStore(database, CoroutineScope(Dispatchers.Unconfined), DeviceIdentity.ephemeral())

  private fun row(
    key: String,
    deviceSeq: Long = 1L,
    originDevice: String = "remote",
    dueDate: Instant = now,
  ) =
    NodeSyncRow(
      positionKey = key,
      dueDate = dueDate,
      lastReview = null,
      firstReview = null,
      stability = 0.0,
      difficulty = 0.0,
      reps = 0,
      lapses = 0,
      phase = "NEW",
      step = 0,
      isDeleted = false,
      updatedAt = now,
      originDevice = originDevice,
      deviceSeq = deviceSeq,
    )

  @Test
  fun applySyncedNodeOnANewPositionWrites() = runTest {
    val treeStore = store()

    val outcome = treeStore.applySyncedNode(row("start"))

    outcome shouldBe ResolutionSource.REMOTE
    treeStore.node(PositionKey("start"))?.positionKey shouldBe PositionKey("start")
  }

  @Test
  fun applySyncedNodeLocalWinsSkipsTheWrite() = runTest {
    val database = InMemoryDatabaseQueryManager()
    val treeStore = store(database)
    // A local row from the same origin device as the incoming remote row, with a higher deviceSeq.
    treeStore.applySyncedNode(row("start", deviceSeq = 5L))

    val outcome = treeStore.applySyncedNode(row("start", deviceSeq = 1L))

    outcome shouldBe ResolutionSource.LOCAL
  }

  @Test
  fun applySyncedNodeEvictsTheCachedEntrySoTheNextReadSeesTheWrite() = runTest {
    val treeStore = store()
    val laterDueDate = Instant.parse("2026-06-01T00:00:00Z")
    treeStore.applySyncedNode(row("start", deviceSeq = 1L, dueDate = now))
    treeStore.node(PositionKey("start")) // warms the cache with the deviceSeq=1 due date

    treeStore.applySyncedNode(row("start", deviceSeq = 2L, dueDate = laterDueDate))

    // If eviction did not happen this would still read the deviceSeq=1 cached due date.
    treeStore.node(PositionKey("start"))?.cardState?.dueDate shouldBe laterDueDate
  }

  @Test
  fun applySyncedMoveOnExistingEndpointsWritesAndFlipsHasGoodOutgoing() = runTest {
    val treeStore = store()
    treeStore.applySyncedNode(row("a"))
    treeStore.applySyncedNode(row("b"))

    val outcome =
      treeStore.applySyncedMove(
        EdgeSyncRow(
          origin = "a",
          destination = "b",
          move = "e4",
          isGood = true,
          isDeleted = false,
          updatedAt = now,
          originDevice = "remote",
          deviceSeq = 1L,
        )
      )

    outcome shouldBe ResolutionSource.REMOTE
    treeStore.node(PositionKey("a"))?.outgoing?.keys shouldBe setOf("e4")
  }
}
