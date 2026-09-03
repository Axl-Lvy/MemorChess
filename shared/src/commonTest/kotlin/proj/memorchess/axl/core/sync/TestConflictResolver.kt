package proj.memorchess.axl.core.sync

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlin.time.Instant

class TestConflictResolver {

  private fun row(
    updatedAt: Instant,
    originDevice: String = "device-a",
    isDeleted: Boolean = false,
    value: String = "v",
  ) =
    SettingSyncRow(
      key = "k",
      value = value,
      isDeleted = isDeleted,
      updatedAt = updatedAt,
      originDevice = originDevice,
    )

  private val t0 = Instant.fromEpochMilliseconds(0)
  private val t1 = Instant.fromEpochMilliseconds(1)
  private val tLarge = Instant.fromEpochMilliseconds(4_102_444_800_000)

  @Test
  fun missingLocalTakesRemote() {
    val remote = row(t1)
    resolve(null, remote) shouldBe Winner(remote, ResolutionSource.REMOTE)
  }

  @Test
  fun missingRemoteKeepsLocal() {
    val local = row(t1)
    resolve(local, null) shouldBe Winner(local, ResolutionSource.LOCAL)
  }

  @Test
  fun bothMissingIsProgrammerError() {
    shouldThrow<IllegalArgumentException> { resolve<SettingSyncRow>(null, null) }
  }

  @Test
  fun laterRemoteWins() {
    val local = row(t0)
    val remote = row(t1, originDevice = "device-b")
    resolve(local, remote) shouldBe Winner(remote, ResolutionSource.REMOTE)
  }

  @Test
  fun laterLocalWins() {
    val local = row(t1)
    val remote = row(t0, originDevice = "device-b")
    resolve(local, remote) shouldBe Winner(local, ResolutionSource.LOCAL)
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
    resolve(local, remote) shouldBe Winner(remote, ResolutionSource.REMOTE)
  }

  @Test
  fun largeTimestampsCompareCorrectly() {
    val local = row(tLarge)
    val remote = row(t1, originDevice = "device-b")
    resolve(local, remote) shouldBe Winner(local, ResolutionSource.LOCAL)
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
    resolve(same, same) shouldBe Winner(same, ResolutionSource.LOCAL)
  }

  @Test
  fun aTombstoneWinsWhenItIsNewer() {
    val local = row(t0)
    val remote = row(t1, originDevice = "device-b", isDeleted = true)
    resolve(local, remote) shouldBe Winner(remote, ResolutionSource.REMOTE)
  }

  @Test
  fun aResurrectionWinsOverAnOlderTombstone() {
    val local = row(t1, value = "alive")
    val remote = row(t0, originDevice = "device-b", isDeleted = true)
    val winner = resolve(local, remote)
    winner shouldBe Winner(local, ResolutionSource.LOCAL)
    winner.row.isDeleted shouldBe false
  }

  @Test
  fun deletionIsNotPrivilegedOverAnEqualTimestampWrite() {
    val local = row(t1, "device-b", isDeleted = false)
    val remote = row(t1, "device-a", isDeleted = true)
    resolve(local, remote) shouldBe Winner(local, ResolutionSource.LOCAL)
  }

  @Test
  fun resolvingIsIdempotent() {
    val local = row(t0)
    val remote = row(t1, originDevice = "device-b")
    val once = resolve(local, remote).row
    resolve(once, remote) shouldBe Winner(once, ResolutionSource.LOCAL)
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
      )
    val newer = base.copy(reps = 1, updatedAt = t1, originDevice = "device-b")
    resolve(base, newer) shouldBe Winner(newer, ResolutionSource.REMOTE)
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
      )
    val older = base.copy(isGood = false, updatedAt = t0, originDevice = "device-b")
    resolve(base, older) shouldBe Winner(base, ResolutionSource.LOCAL)
  }
}
