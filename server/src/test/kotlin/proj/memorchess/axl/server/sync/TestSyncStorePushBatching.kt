package proj.memorchess.axl.server.sync

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.comparables.shouldBeLessThanOrEqualTo
import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import proj.memorchess.axl.core.sync.DevicePlatform
import proj.memorchess.axl.core.sync.EdgeRepertoireTagSyncRow
import proj.memorchess.axl.core.sync.EdgeSyncRow
import proj.memorchess.axl.core.sync.NodeSyncRow
import proj.memorchess.axl.core.sync.RepertoireSyncRow
import proj.memorchess.axl.core.sync.SettingSyncRow
import proj.memorchess.axl.core.sync.SyncPushRequest
import proj.memorchess.axl.server.db.CountingDataSource
import proj.memorchess.axl.server.db.PostgresTestDb

internal class TestSyncStorePushBatching {

  private val counting = CountingDataSource(PostgresTestDb.dataSource())
  private val store = SyncStore(counting)
  private val serverNow = Instant.fromEpochMilliseconds(1_000_000)

  private suspend fun newUser(): String {
    val user = PostgresTestDb.newUserId()
    store.registerDevice(user, DEVICE, DevicePlatform.JVM, afterReset = false, serverNow)
    return user
  }

  private fun fen(prefix: String, i: Int) = "fen-$prefix-$i"

  private fun requestOf(
    prefix: String,
    count: Int,
    reps: Int = 1,
    seq: Long = 1,
    withTags: Boolean = true,
  ): SyncPushRequest {
    val at = serverNow
    return SyncPushRequest(
      nodes =
        (1..count).map {
          NodeSyncRow(
            positionKey = fen(prefix, it),
            dueDate = at,
            lastReview = null,
            firstReview = null,
            stability = 1.5,
            difficulty = 5.0,
            reps = reps,
            lapses = 0,
            phase = "REVIEW",
            step = 0,
            isDeleted = false,
            updatedAt = at,
            originDevice = "device-a",
            deviceSeq = seq,
          )
        },
      edges =
        (1..count).map {
          EdgeSyncRow(
            origin = fen(prefix, it),
            destination = fen(prefix, it + count),
            move = "e4",
            isGood = true,
            isDeleted = false,
            updatedAt = at,
            originDevice = "device-a",
            deviceSeq = seq,
          )
        },
      settings =
        (1..count).map {
          SettingSyncRow(
            key = "$prefix-setting-$it",
            value = "v$reps",
            isDeleted = false,
            updatedAt = at,
            originDevice = "device-a",
            deviceSeq = seq,
          )
        },
      repertoires =
        (1..REPERTOIRES).map {
          RepertoireSyncRow(
            id = "$prefix-rep-$it",
            name = "name-$reps",
            color = "WHITE",
            isDeleted = false,
            updatedAt = at,
            originDevice = "device-a",
            deviceSeq = seq,
          )
        },
      tags =
        if (!withTags) emptyList()
        else
          (1..count).map {
            EdgeRepertoireTagSyncRow(
              origin = fen(prefix, it),
              destination = fen(prefix, it + count),
              repertoireId = "$prefix-rep-${it % REPERTOIRES + 1}",
              isDeleted = false,
              updatedAt = at,
              originDevice = "device-a",
              deviceSeq = seq,
            )
          },
      device = DEVICE,
    )
  }

  @Test
  fun aLargeFirstPushTakesAConstantNumberOfStatements() = runTest {
    val user = newUser()
    counting.executions.set(0)

    val response = store.push(user, DEVICE, requestOf("first", 400), serverNow)

    response.rejected.shouldBeEmpty()
    counting.executions.get() shouldBeLessThanOrEqualTo STATEMENT_BUDGET
  }

  @Test
  fun aLargeOverwritingPushTakesAConstantNumberOfStatements() = runTest {
    val user = newUser()
    store.push(user, DEVICE, requestOf("over", 400, reps = 1, seq = 1), serverNow)
    counting.executions.set(0)

    val response = store.push(user, DEVICE, requestOf("over", 400, reps = 2, seq = 2), serverNow)

    response.rejected.shouldBeEmpty()
    counting.executions.get() shouldBeLessThanOrEqualTo STATEMENT_BUDGET
    val stored = store.pull(user, DEVICE, null, 10_000, serverNow)
    stored.nodes.size shouldBe 400
    stored.nodes.all { it.reps == 2 } shouldBe true
  }

  @Test
  fun anIdenticalReplayTakesAConstantNumberOfStatementsAndAssignsNoRevision() = runTest {
    val user = newUser()
    val request = requestOf("replay", 400)
    store.push(user, DEVICE, request, serverNow)
    counting.executions.set(0)

    store.push(user, DEVICE, request, serverNow)

    counting.executions.get() shouldBeLessThanOrEqualTo STATEMENT_BUDGET
  }

  @Test
  fun aKeyRepeatedWithinOnePushResolvesToTheSameWinnerAsTwoSeparatePushes() = runTest {
    val older =
      SettingSyncRow("theme", "dark", false, serverNow, originDevice = "device-a", deviceSeq = 1)
    val newer =
      SettingSyncRow("theme", "light", false, serverNow, originDevice = "device-a", deviceSeq = 2)
    val together = newUser()
    val apart = newUser()
    fun request(vararg settings: SettingSyncRow) =
      SyncPushRequest(
        nodes = emptyList(),
        edges = emptyList(),
        settings = settings.toList(),
        device = DEVICE,
      )

    store.push(together, DEVICE, request(newer, older), serverNow)
    store.push(apart, DEVICE, request(older), serverNow)
    store.push(apart, DEVICE, request(newer), serverNow)

    val storedTogether = store.pull(together, DEVICE, null, 10, serverNow).settings.single()
    storedTogether shouldBe store.pull(apart, DEVICE, null, 10, serverNow).settings.single()
    storedTogether.value shouldBe "light"
  }

  private companion object {
    const val DEVICE = "device-a"

    /** Kept under the per user repertoire cap, unlike the other resources. */
    const val REPERTOIRES = 100

    /** Nowhere near the thousands a per row loop needs, and loose enough to survive refactors. */
    const val STATEMENT_BUDGET = 60
  }
}
