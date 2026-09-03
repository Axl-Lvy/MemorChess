package proj.memorchess.axl.core.sync

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlin.time.Instant

internal class TestConflictResolver {

  private fun row(
    updatedAt: Instant,
    originDevice: String = "device-a",
    isDeleted: Boolean = false,
    value: String = "v",
    deviceSeq: Long = 0,
  ) =
    SettingSyncRow(
      key = "k",
      value = value,
      isDeleted = isDeleted,
      updatedAt = updatedAt,
      originDevice = originDevice,
      deviceSeq = deviceSeq,
    )

  private val t0 = Instant.fromEpochMilliseconds(0)
  private val t1 = Instant.fromEpochMilliseconds(1)
  private val tLarge = Instant.fromEpochMilliseconds(4_102_444_800_000)

  @Test
  fun missingLocalTakesRemote() {
    val remote = row(t1)
    resolve(null, remote) shouldBe Resolution(remote, ResolutionSource.REMOTE)
  }

  @Test
  fun missingRemoteKeepsLocal() {
    val local = row(t1)
    resolve(local, null) shouldBe Resolution(local, ResolutionSource.LOCAL)
  }

  @Test
  fun bothMissingIsProgrammerError() {
    shouldThrow<IllegalArgumentException> { resolve<SettingSyncRow>(null, null) }
  }

  @Test
  fun laterRemoteWins() {
    val local = row(t0)
    val remote = row(t1, originDevice = "device-b")
    resolve(local, remote) shouldBe Resolution(remote, ResolutionSource.REMOTE)
  }

  @Test
  fun laterLocalWins() {
    val local = row(t1)
    val remote = row(t0, originDevice = "device-b")
    resolve(local, remote) shouldBe Resolution(local, ResolutionSource.LOCAL)
  }

  @Test
  fun oneMillisecondEitherSideOfTheBoundaryDecides() {
    resolve(row(t0), row(t1, originDevice = "device-b")).source shouldBe ResolutionSource.REMOTE
    resolve(row(t1), row(t0, originDevice = "device-b")).source shouldBe ResolutionSource.LOCAL
  }

  @Test
  fun epochZeroIsAnOrdinaryTimestamp() {
    val local = row(t0, originDevice = "device-a")
    val remote = row(t0, originDevice = "device-b")
    resolve(local, remote) shouldBe Resolution(remote, ResolutionSource.REMOTE)
  }

  @Test
  fun largeTimestampsCompareCorrectly() {
    val local = row(tLarge)
    val remote = row(t1, originDevice = "device-b")
    resolve(local, remote) shouldBe Resolution(local, ResolutionSource.LOCAL)
  }

  @Test
  fun tiedTimestampsBreakOnGreaterDeviceId() {
    resolve(row(t1, "device-a"), row(t1, "device-b")).source shouldBe ResolutionSource.REMOTE
    resolve(row(t1, "device-b"), row(t1, "device-a")).source shouldBe ResolutionSource.LOCAL
  }

  @Test
  fun theTiebreakIsSymmetricSoDevicesCannotFlipForever() {
    val a = row(t1, "device-a")
    val b = row(t1, "device-b")
    resolve(a, b).row shouldBe resolve(b, a).row
  }

  @Test
  fun identicalRowsKeepLocalWithoutAWrite() {
    val same = row(t1, "device-a")
    resolve(same, same) shouldBe Resolution(same, ResolutionSource.LOCAL)
  }

  @Test
  fun aTombstoneWinsWhenItIsNewer() {
    val local = row(t0)
    val remote = row(t1, originDevice = "device-b", isDeleted = true)
    resolve(local, remote) shouldBe Resolution(remote, ResolutionSource.REMOTE)
  }

  @Test
  fun aResurrectionWinsOverAnOlderTombstone() {
    val local = row(t1, value = "alive")
    val remote = row(t0, originDevice = "device-b", isDeleted = true)
    val winner = resolve(local, remote)
    winner shouldBe Resolution(local, ResolutionSource.LOCAL)
    winner.row.isDeleted shouldBe false
  }

  @Test
  fun deletionIsNotPrivilegedOverAnEqualTimestampWrite() {
    val local = row(t1, "device-b", isDeleted = false)
    val remote = row(t1, "device-a", isDeleted = true)
    resolve(local, remote) shouldBe Resolution(local, ResolutionSource.LOCAL)
  }

  @Test
  fun resolvingIsIdempotent() {
    val local = row(t0)
    val remote = row(t1, originDevice = "device-b")
    val once = resolve(local, remote).row
    resolve(once, remote) shouldBe Resolution(once, ResolutionSource.LOCAL)
  }

  @Test
  fun sameDeviceIgnoresTheClockEntirelyAndTrustsItsSequence() {
    // The device's clock jumped backwards between the two writes. The newer write, identified by
    // the greater sequence, must still win. Regression test for randomised seeds 5 and 43.
    val older = row(tLarge, "device-a", deviceSeq = 1)
    val newer = row(t0, "device-a", deviceSeq = 2, value = "newer")
    resolve(older, newer) shouldBe Resolution(newer, ResolutionSource.REMOTE)
    resolve(newer, older) shouldBe Resolution(newer, ResolutionSource.LOCAL)
  }

  @Test
  fun sameDeviceSameInstantBreaksOnTheGreaterSequence() {
    val earlier = row(t1, "device-a", deviceSeq = 1)
    val later = row(t1, "device-a", deviceSeq = 2, value = "later")
    resolve(earlier, later) shouldBe Resolution(later, ResolutionSource.REMOTE)
    resolve(later, earlier) shouldBe Resolution(later, ResolutionSource.LOCAL)
  }

  @Test
  fun theSequenceIsOnlyConsultedAfterTheDeviceId() {
    // A high sequence on one device must not beat the device ordering, because sequences from
    // different devices are unrelated counters.
    val local = row(t1, "device-b", deviceSeq = 0)
    val remote = row(t1, "device-a", deviceSeq = 99)
    resolve(local, remote) shouldBe Resolution(local, ResolutionSource.LOCAL)
  }

  @Test
  fun resolveIsCommutativeForEveryDistinctPair() {
    // The property that actually guarantees convergence: both sides pick the same row whichever
    // way round they ask. Regression test for randomised seed 10, where two versions tied on both
    // updatedAt and originDevice and each side kept its own.
    val rows =
      listOf(
        row(t0, "device-a", deviceSeq = 0),
        row(t0, "device-a", deviceSeq = 1, value = "a1"),
        row(t0, "device-b", deviceSeq = 0),
        row(t1, "device-a", deviceSeq = 2),
        row(t1, "device-b", deviceSeq = 5),
        row(tLarge, "device-a", deviceSeq = 3),
      )
    for (left in rows) {
      for (right in rows) {
        resolve(left, right).row shouldBe resolve(right, left).row
      }
    }
  }

  @Test
  fun nodeRowsResolveWithTheSameFunction() {
    val base =
      NodeSyncRow(
        positionKey = "k",
        dueDate = t0,
        lastReview = null,
        firstReview = null,
        stability = 0.0,
        difficulty = 0.0,
        reps = 0,
        lapses = 0,
        phase = "NEW",
        step = 0,
        isDeleted = false,
        updatedAt = t0,
        originDevice = "device-a",
        deviceSeq = 0,
      )
    val newer = base.copy(reps = 1, updatedAt = t1, originDevice = "device-b")
    resolve(base, newer) shouldBe Resolution(newer, ResolutionSource.REMOTE)
  }

  @Test
  fun edgeRowsResolveWithTheSameFunction() {
    val base =
      EdgeSyncRow(
        origin = "a",
        destination = "b",
        move = "e4",
        isGood = true,
        isDeleted = false,
        updatedAt = t1,
        originDevice = "device-a",
        deviceSeq = 0,
      )
    val older = base.copy(isGood = false, updatedAt = t0, originDevice = "device-b")
    resolve(base, older) shouldBe Resolution(base, ResolutionSource.LOCAL)
  }
}
