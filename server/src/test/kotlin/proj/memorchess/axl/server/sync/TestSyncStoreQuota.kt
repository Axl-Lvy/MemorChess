package proj.memorchess.axl.server.sync

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.string.shouldContain
import kotlin.test.Test
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import proj.memorchess.axl.core.sync.EdgeRepertoireTagSyncRow
import proj.memorchess.axl.core.sync.EdgeSyncRow
import proj.memorchess.axl.core.sync.NodeSyncRow
import proj.memorchess.axl.core.sync.RepertoireSyncRow
import proj.memorchess.axl.core.sync.SettingSyncRow
import proj.memorchess.axl.core.sync.SyncPushRequest
import proj.memorchess.axl.server.db.PostgresTestDb

/**
 * Verifies the per user row count quota [SyncStore.push] enforces on each resource but settings.
 */
internal class TestSyncStoreQuota {

  private val serverNow = Instant.fromEpochMilliseconds(1_000_000)

  private fun node(key: String, seq: Long = 1) =
    NodeSyncRow(
      positionKey = key,
      dueDate = serverNow,
      lastReview = null,
      firstReview = null,
      stability = 1.5,
      difficulty = 5.0,
      reps = 0,
      lapses = 0,
      phase = "REVIEW",
      step = 0,
      isDeleted = false,
      updatedAt = serverNow,
      originDevice = "device-a",
      deviceSeq = seq,
    )

  private fun edge(origin: String, destination: String, seq: Long = 1) =
    EdgeSyncRow(
      origin = origin,
      destination = destination,
      move = "e4",
      isGood = true,
      isDeleted = false,
      updatedAt = serverNow,
      originDevice = "device-a",
      deviceSeq = seq,
    )

  private fun repertoire(id: String, seq: Long = 1) =
    RepertoireSyncRow(
      id = id,
      name = id,
      color = "WHITE",
      isDeleted = false,
      updatedAt = serverNow,
      originDevice = "device-a",
      deviceSeq = seq,
    )

  private fun tag(origin: String, destination: String, repertoireId: String, seq: Long = 1) =
    EdgeRepertoireTagSyncRow(
      origin = origin,
      destination = destination,
      repertoireId = repertoireId,
      isDeleted = false,
      updatedAt = serverNow,
      originDevice = "device-a",
      deviceSeq = seq,
    )

  private fun setting(key: String, seq: Long = 1) =
    SettingSyncRow(
      key = key,
      value = "v",
      isDeleted = false,
      updatedAt = serverNow,
      originDevice = "device-a",
      deviceSeq = seq,
    )

  private fun fen(suffix: String) = "fen-${System.nanoTime()}-$suffix"

  @Test
  fun `refuses a push that would cross the node cap`() = runTest {
    val store = SyncStore(PostgresTestDb.dataSource(), maxNodesPerUser = 1)
    val user = PostgresTestDb.newUserId()
    store.push(
      user,
      SyncPushRequest(listOf(node(fen("a"))), emptyList(), emptyList()),
      serverNow,
    )

    val exception =
      shouldThrow<QuotaExceededException> {
        store.push(
          user,
          SyncPushRequest(listOf(node(fen("b"))), emptyList(), emptyList()),
          serverNow,
        )
      }
    exception.message shouldContain "1"
  }

  @Test
  fun `updating an already owned node at the cap is not refused`() = runTest {
    val store = SyncStore(PostgresTestDb.dataSource(), maxNodesPerUser = 1)
    val user = PostgresTestDb.newUserId()
    val key = fen("owned")
    store.push(
      user,
      SyncPushRequest(listOf(node(key, seq = 1)), emptyList(), emptyList()),
      serverNow,
    )

    store
      .push(user, SyncPushRequest(listOf(node(key, seq = 2)), emptyList(), emptyList()), serverNow)
      .rejected
      .shouldBeEmpty()
  }

  @Test
  fun `refuses a push that would cross the edge cap`() = runTest {
    val store = SyncStore(PostgresTestDb.dataSource(), maxEdgesPerUser = 1)
    val user = PostgresTestDb.newUserId()
    store.push(
      user,
      SyncPushRequest(emptyList(), listOf(edge(fen("o1"), fen("d1"))), emptyList()),
      serverNow,
    )

    shouldThrow<QuotaExceededException> {
      store.push(
        user,
        SyncPushRequest(emptyList(), listOf(edge(fen("o2"), fen("d2"))), emptyList()),
        serverNow,
      )
    }
  }

  @Test
  fun `updating an already owned edge at the cap is not refused`() = runTest {
    val store = SyncStore(PostgresTestDb.dataSource(), maxEdgesPerUser = 1)
    val user = PostgresTestDb.newUserId()
    val origin = fen("o")
    val destination = fen("d")
    store.push(
      user,
      SyncPushRequest(emptyList(), listOf(edge(origin, destination, seq = 1)), emptyList()),
      serverNow,
    )

    store
      .push(
        user,
        SyncPushRequest(emptyList(), listOf(edge(origin, destination, seq = 2)), emptyList()),
        serverNow,
      )
      .rejected
      .shouldBeEmpty()
  }

  @Test
  fun `refuses a push that would cross the repertoire cap`() = runTest {
    val store = SyncStore(PostgresTestDb.dataSource(), maxRepertoiresPerUser = 1)
    val user = PostgresTestDb.newUserId()
    store.push(
      user,
      SyncPushRequest(
        emptyList(),
        emptyList(),
        emptyList(),
        repertoires = listOf(repertoire("italian-game")),
      ),
      serverNow,
    )

    shouldThrow<QuotaExceededException> {
      store.push(
        user,
        SyncPushRequest(
          emptyList(),
          emptyList(),
          emptyList(),
          repertoires = listOf(repertoire("french-defense")),
        ),
        serverNow,
      )
    }
  }

  @Test
  fun `refuses a push that would cross the tag cap`() = runTest {
    val store = SyncStore(PostgresTestDb.dataSource(), maxTagsPerUser = 1)
    val user = PostgresTestDb.newUserId()
    val firstEdge = edge(fen("to1"), fen("td1"))
    val secondEdge = edge(fen("to2"), fen("td2"))
    store.push(
      user,
      SyncPushRequest(
        emptyList(),
        listOf(firstEdge, secondEdge),
        emptyList(),
        tags = listOf(tag(firstEdge.origin, firstEdge.destination, "italian-game")),
      ),
      serverNow,
    )

    shouldThrow<QuotaExceededException> {
      store.push(
        user,
        SyncPushRequest(
          emptyList(),
          emptyList(),
          emptyList(),
          tags = listOf(tag(secondEdge.origin, secondEdge.destination, "italian-game")),
        ),
        serverNow,
      )
    }
  }

  @Test
  fun `settings never count against any quota`() = runTest {
    // Every capped resource is at its cap of zero; only settings, which has none, may still write.
    val store =
      SyncStore(
        PostgresTestDb.dataSource(),
        maxNodesPerUser = 0,
        maxEdgesPerUser = 0,
        maxRepertoiresPerUser = 0,
        maxTagsPerUser = 0,
      )
    val user = PostgresTestDb.newUserId()

    store
      .push(user, SyncPushRequest(emptyList(), emptyList(), listOf(setting("theme"))), serverNow)
      .rejected
      .shouldBeEmpty()
  }
}
