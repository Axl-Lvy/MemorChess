package proj.memorchess.axl.core.sync

import io.kotest.assertions.withClue
import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.shouldBe
import kotlin.random.Random
import kotlin.test.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

internal class TestSyncConvergence {

  private fun at(millis: Long) = Instant.fromEpochMilliseconds(millis)

  private val serverNow = at(1_000_000)

  @Test
  fun oneDeviceRoundTripsThroughTheServer() {
    val server = FakeServer()
    val a = FakeClient("device-a")

    a.edit("theme", "dark", at(10))
    a.sync(server, serverNow)

    server.snapshot()["theme"]?.value shouldBe "dark"
    a.visible() shouldBe mapOf("theme" to "dark")
  }

  @Test
  fun aSecondDeviceReceivesTheFirstDevicesRows() {
    val server = FakeServer()
    val a = FakeClient("device-a")
    val b = FakeClient("device-b")

    a.edit("theme", "dark", at(10))
    a.sync(server, serverNow)
    b.sync(server, serverNow)

    b.visible() shouldBe mapOf("theme" to "dark")
  }

  @Test
  fun concurrentOfflineEditsConvergeOnTheLaterWrite() {
    val server = FakeServer()
    val a = FakeClient("device-a")
    val b = FakeClient("device-b")

    a.edit("theme", "dark", at(10))
    b.edit("theme", "light", at(20))

    a.sync(server, serverNow)
    b.sync(server, serverNow)
    a.sync(server, serverNow)

    a.visible() shouldBe mapOf("theme" to "light")
    b.visible() shouldBe mapOf("theme" to "light")
  }

  @Test
  fun theEarlierWriterSyncingLastStillLoses() {
    val server = FakeServer()
    val a = FakeClient("device-a")
    val b = FakeClient("device-b")

    a.edit("theme", "dark", at(10))
    b.edit("theme", "light", at(20))

    b.sync(server, serverNow)
    a.sync(server, serverNow)
    b.sync(server, serverNow)

    a.visible() shouldBe mapOf("theme" to "light")
    b.visible() shouldBe mapOf("theme" to "light")
  }

  @Test
  fun aDeletionPropagatesToTheOtherDevice() {
    val server = FakeServer()
    val a = FakeClient("device-a")
    val b = FakeClient("device-b")

    a.edit("theme", "dark", at(10))
    a.sync(server, serverNow)
    b.sync(server, serverNow)

    a.delete("theme", at(20))
    a.sync(server, serverNow)
    b.sync(server, serverNow)

    b.visible().shouldBeEmpty()
    b.snapshot()["theme"]?.isDeleted shouldBe true
  }

  @Test
  fun aNewerEditResurrectsARowDeletedElsewhere() {
    val server = FakeServer()
    val a = FakeClient("device-a")
    val b = FakeClient("device-b")

    a.edit("theme", "dark", at(10))
    a.sync(server, serverNow)
    b.sync(server, serverNow)

    a.delete("theme", at(20))
    b.edit("theme", "light", at(30))

    a.sync(server, serverNow)
    b.sync(server, serverNow)
    a.sync(server, serverNow)

    a.visible() shouldBe mapOf("theme" to "light")
    b.visible() shouldBe mapOf("theme" to "light")
  }

  @Test
  fun aFutureTimestampIsRefusedAndNothingIsStored() {
    val server = FakeServer()
    val a = FakeClient("device-a")

    a.edit("theme", "dark", at(40_000_000_000))
    a.sync(server, serverNow)

    server.snapshot().shouldBeEmpty()
  }

  @Test
  fun aRefusedRowIsReStampedAndLandsOnTheNextRound() {
    val server = FakeServer()
    val a = FakeClient("device-a")

    a.edit("theme", "dark", at(40_000_000_000))
    a.sync(server, serverNow)
    a.sync(server, serverNow)

    server.snapshot()["theme"]?.value shouldBe "dark"
    server.snapshot()["theme"]?.updatedAt shouldBe serverNow
    a.snapshot()["theme"]?.updatedAt shouldBe serverNow
  }

  @Test
  fun aReStampedRowIsByteIdenticalOnBothSides() {
    val server = FakeServer()
    val a = FakeClient("device-a")

    a.edit("theme", "dark", at(40_000_000_000))
    a.sync(server, serverNow)
    a.sync(server, serverNow)

    // The refusal exists so that the server holds exactly what the author holds.
    a.snapshot()["theme"] shouldBe server.snapshot()["theme"]
  }

  @Test
  fun aDeviceWithABrokenClockStillConvergesWithAnHonestDevice() {
    val server = FakeServer()
    val a = FakeClient("device-a")
    val b = FakeClient("device-b")

    a.edit("theme", "dark", at(40_000_000_000))
    b.edit("theme", "light", at(10))

    repeat(3) {
      a.sync(server, serverNow)
      b.sync(server, serverNow)
    }

    a.visible() shouldBe b.visible()
    a.snapshot() shouldBe b.snapshot()
  }

  @Test
  fun aRowExactlyAtTheToleranceIsAccepted() {
    val server = FakeServer()
    val a = FakeClient("device-a")

    a.edit("theme", "dark", serverNow + SYNC_SKEW_TOLERANCE)
    a.sync(server, serverNow)

    server.snapshot()["theme"]?.value shouldBe "dark"
  }

  @Test
  fun aRowOneMillisecondInsideTheToleranceIsAccepted() {
    val server = FakeServer()
    val a = FakeClient("device-a")

    a.edit("theme", "dark", serverNow + SYNC_SKEW_TOLERANCE - 1.milliseconds)
    a.sync(server, serverNow)

    server.snapshot()["theme"]?.value shouldBe "dark"
  }

  @Test
  fun aRowOneMillisecondBeyondTheToleranceIsRefused() {
    val server = FakeServer()
    val a = FakeClient("device-a")

    a.edit("theme", "dark", serverNow + SYNC_SKEW_TOLERANCE + 1.milliseconds)
    a.sync(server, serverNow)

    server.snapshot().shouldBeEmpty()
  }

  @Test
  fun aClockJumpingBackwardsCannotResurrectADeletedRow() {
    val server = FakeServer()
    val a = FakeClient("device-a")
    val b = FakeClient("device-b")

    // A badly fast clock gets refused, then re-stamped to server time.
    a.edit("sound", "on", at(40_000_000_000))
    a.sync(server, serverNow)
    a.sync(server, serverNow)

    // The corrected clock is far earlier than the held row, so only a strictly increasing local
    // stamp keeps this delete from losing to it.
    a.delete("sound", at(22))
    a.sync(server, serverNow)
    b.sync(server, serverNow)

    a.visible().shouldBeEmpty()
    b.visible().shouldBeEmpty()
    a.snapshot() shouldBe b.snapshot()
  }

  @Test
  fun aDeviceWhosePushLosesLearnsTheTruthInsteadOfDiverging() {
    val server = FakeServer()
    val a = FakeClient("device-a")
    val b = FakeClient("device-b")

    a.edit("theme", "dark", at(500))
    a.sync(server, serverNow)
    b.sync(server, serverNow)

    // An older write for the same key, which loses on the server.
    b.delete("theme", at(29))
    b.sync(server, serverNow)

    // The surviving row sits at a revision b's cursor already passed, yet b must still receive it.
    b.sync(server, serverNow)

    b.visible() shouldBe mapOf("theme" to "dark")
    a.snapshot() shouldBe b.snapshot()
  }

  @Test
  fun syncingWithNothingDirtyChangesNothing() {
    val server = FakeServer()
    val a = FakeClient("device-a")

    a.sync(server, serverNow)
    a.sync(server, serverNow)

    a.snapshot().shouldBeEmpty()
    server.snapshot().shouldBeEmpty()
    server.currentRevision() shouldBe 0L
  }

  @Test
  fun repeatedEditsToOneKeyCollapseIntoASingleServerRevision() {
    val server = FakeServer()
    val a = FakeClient("device-a")

    a.edit("theme", "one", at(10))
    a.edit("theme", "two", at(20))
    a.edit("theme", "three", at(30))
    a.sync(server, serverNow)

    server.currentRevision() shouldBe 1L
    server.snapshot()["theme"]?.value shouldBe "three"
  }

  @Test
  fun randomisedInterleavingsAlwaysConverge() {
    val keys = listOf("theme", "language", "boardSize", "sound")
    repeat(200) { seed ->
      val random = Random(seed)
      val server = FakeServer()
      val a = FakeClient("device-a")
      val b = FakeClient("device-b")
      var clock = 1L

      repeat(30) {
        val client = if (random.nextBoolean()) a else b
        // device-a's clock is badly wrong a third of the time, so broken clocks are covered.
        val skew = if (client === a && random.nextInt(3) == 0) 40_000_000_000L else 0L
        when (random.nextInt(4)) {
          0 -> client.edit(keys.random(random), "v$clock", at(clock + skew))
          1 -> client.delete(keys.random(random), at(clock + skew))
          else -> client.sync(server, serverNow)
        }
        clock++
      }

      repeat(3) {
        a.sync(server, serverNow)
        b.sync(server, serverNow)
      }

      withClue("seed $seed") {
        a.snapshot() shouldBe b.snapshot()
        a.visible() shouldBe b.visible()
      }
    }
  }
}
