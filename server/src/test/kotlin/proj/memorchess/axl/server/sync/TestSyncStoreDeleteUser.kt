package proj.memorchess.axl.server.sync

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
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
import proj.memorchess.axl.server.db.resolvePositionIds

internal class TestSyncStoreDeleteUser {

  private val store = SyncStore(PostgresTestDb.dataSource())
  private val serverNow = Instant.fromEpochMilliseconds(1_000_000)

  private fun fen(suffix: String) = "fen-${System.nanoTime()}-$suffix"

  private suspend fun populate(user: String, origin: String, destination: String) {
    store.push(
      user,
      SyncPushRequest(
        nodes =
          listOf(
            NodeSyncRow(
              positionKey = origin,
              dueDate = serverNow,
              lastReview = null,
              firstReview = null,
              stability = 1.0,
              difficulty = 1.0,
              reps = 0,
              lapses = 0,
              phase = "NEW",
              step = 0,
              isDeleted = false,
              updatedAt = serverNow,
              originDevice = "device-a",
              deviceSeq = 1,
            )
          ),
        edges =
          listOf(
            EdgeSyncRow(
              origin = origin,
              destination = destination,
              move = "e4",
              isGood = true,
              isDeleted = false,
              updatedAt = serverNow,
              originDevice = "device-a",
              deviceSeq = 1,
            )
          ),
        settings =
          listOf(
            SettingSyncRow(
              key = "theme",
              value = "dark",
              isDeleted = false,
              updatedAt = serverNow,
              originDevice = "device-a",
              deviceSeq = 1,
            )
          ),
        repertoires =
          listOf(
            RepertoireSyncRow(
              id = "italian-game",
              name = "Italian Game",
              color = "WHITE",
              isDeleted = false,
              updatedAt = serverNow,
              originDevice = "device-a",
              deviceSeq = 1,
            )
          ),
        tags =
          listOf(
            EdgeRepertoireTagSyncRow(
              origin = origin,
              destination = destination,
              repertoireId = "italian-game",
              isDeleted = false,
              updatedAt = serverNow,
              originDevice = "device-a",
              deviceSeq = 1,
            )
          ),
      ),
      serverNow,
    )
  }

  @Test
  fun deletingAUserRemovesEveryOneOfItsRows() = runTest {
    val user = PostgresTestDb.newUserId()
    val origin = fen("o")
    populate(user, origin, fen("d"))

    store.deleteUser(user)

    val page = store.pull(user, 0, 100, serverNow)
    page.nodes.shouldBeEmpty()
    page.edges.shouldBeEmpty()
    page.settings.shouldBeEmpty()
    page.repertoires.shouldBeEmpty()
    page.tags.shouldBeEmpty()
  }

  @Test
  fun deletingAUserLeavesTheSharedTablesAlone() = runTest {
    val user = PostgresTestDb.newUserId()
    val origin = fen("shared-o")
    populate(user, origin, fen("shared-d"))

    val idBefore =
      PostgresTestDb.dataSource().connection.use { it.resolvePositionIds(listOf(origin)) }[origin]

    store.deleteUser(user)

    // Another user may reference this position, and the shared tables are append only.
    val idAfter =
      PostgresTestDb.dataSource().connection.use { it.resolvePositionIds(listOf(origin)) }[origin]
    idAfter shouldBe idBefore
  }

  @Test
  fun deletingAUserWithNoRowsIsANoOp() = runTest { store.deleteUser(PostgresTestDb.newUserId()) }

  @Test
  fun deletingOneUserDoesNotTouchAnother() = runTest {
    val mine = PostgresTestDb.newUserId()
    val theirs = PostgresTestDb.newUserId()
    populate(mine, fen("mine-o"), fen("mine-d"))
    populate(theirs, fen("theirs-o"), fen("theirs-d"))

    store.deleteUser(mine)

    val page = store.pull(theirs, 0, 100, serverNow)
    page.settings.single().value shouldBe "dark"
    page.repertoires.single().id shouldBe "italian-game"
    page.tags.single().repertoireId shouldBe "italian-game"
  }
}
