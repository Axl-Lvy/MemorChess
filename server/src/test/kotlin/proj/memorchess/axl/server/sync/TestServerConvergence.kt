package proj.memorchess.axl.server.sync

import io.kotest.assertions.withClue
import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.shouldBe
import kotlin.random.Random
import kotlin.test.Test
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import proj.memorchess.axl.server.db.PostgresTestDb

/**
 * The same property `:shared` proves against fakes, re-run against real Postgres.
 *
 * A failure here means the SQL disagrees with the semantics `:shared` proved, which no cheaper test
 * can catch. Fifty seeds rather than two hundred: every operation is a real database round trip.
 */
internal class TestServerConvergence {

  private val store = SyncStore(PostgresTestDb.dataSource())

  private fun at(millis: Long) = Instant.fromEpochMilliseconds(millis)

  private val serverNow = at(1_000_000)

  @Test
  fun twoDevicesConvergeOnASingleKey() = runTest {
    val user = PostgresTestDb.newUserId()
    val a = TestDevice("device-a", user)
    val b = TestDevice("device-b", user)

    a.edit("theme", "dark", at(10))
    b.edit("theme", "light", at(20))

    repeat(3) {
      a.sync(store, serverNow)
      b.sync(store, serverNow)
    }

    a.visible() shouldBe mapOf("theme" to "light")
    b.visible() shouldBe mapOf("theme" to "light")
  }

  @Test
  fun aRefusedFutureRowLandsOnTheNextRound() = runTest {
    val user = PostgresTestDb.newUserId()
    val a = TestDevice("device-a", user)

    a.edit("theme", "dark", at(40_000_000_000))
    a.sync(store, serverNow)
    a.sync(store, serverNow)

    store.readSettingForTest(user, "theme")?.value shouldBe "dark"
    store.readSettingForTest(user, "theme")?.updatedAt shouldBe serverNow
  }

  @Test
  fun aClockJumpingBackwardsCannotResurrectADeletedRow() = runTest {
    val user = PostgresTestDb.newUserId()
    val a = TestDevice("device-a", user)
    val b = TestDevice("device-b", user)

    a.edit("sound", "on", at(40_000_000_000))
    a.sync(store, serverNow)
    a.sync(store, serverNow)

    // The clock is corrected, so this delete's wall clock is far earlier than the live row.
    a.delete("sound", at(22))
    a.sync(store, serverNow)
    b.sync(store, serverNow)

    a.visible().shouldBeEmpty()
    b.visible().shouldBeEmpty()
  }

  @Test
  fun aDeviceWhosePushLosesLearnsTheTruth() = runTest {
    val user = PostgresTestDb.newUserId()
    val a = TestDevice("device-a", user)
    val b = TestDevice("device-b", user)

    a.edit("theme", "dark", at(500))
    a.sync(store, serverNow)
    b.sync(store, serverNow)

    b.delete("theme", at(29))
    b.sync(store, serverNow)
    b.sync(store, serverNow)

    b.visible() shouldBe mapOf("theme" to "dark")
    a.snapshot() shouldBe b.snapshot()
  }

  @Test
  fun randomisedInterleavingsAlwaysConverge() = runTest {
    val keys = listOf("theme", "language", "boardSize", "sound")
    repeat(50) { seed ->
      val random = Random(seed)
      val user = PostgresTestDb.newUserId()
      val a = TestDevice("device-a", user)
      val b = TestDevice("device-b", user)
      var clock = 1L

      repeat(20) {
        val device = if (random.nextBoolean()) a else b
        // device-a's clock is badly wrong a third of the time.
        val skew = if (device === a && random.nextInt(3) == 0) 40_000_000_000L else 0L
        when (random.nextInt(4)) {
          0 -> device.edit(keys.random(random), "v$clock", at(clock + skew))
          1 -> device.delete(keys.random(random), at(clock + skew))
          else -> device.sync(store, serverNow)
        }
        clock++
      }

      repeat(3) {
        a.sync(store, serverNow)
        b.sync(store, serverNow)
      }

      withClue("seed $seed") {
        a.snapshot() shouldBe b.snapshot()
        a.visible() shouldBe b.visible()
      }
    }
  }
}
