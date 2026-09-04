package proj.memorchess.axl.core.sync

import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlin.time.Instant
import proj.memorchess.axl.test_util.TestSettings

class TestSyncJobStore {

  @Test
  fun freshStoreReadsIdle() {
    val store = SyncJobStore(TestSettings())
    store.read() shouldBe SyncJobState.IDLE
  }

  @Test
  fun writeThenReadRoundTrips() {
    val store = SyncJobStore(TestSettings())
    val state =
      SyncJobState(SyncJobStatus.BACKING_OFF, Instant.parse("2026-01-01T00:00:00Z"), attempt = 3)

    store.write(state)

    store.read() shouldBe state
  }

  @Test
  fun writeWithNullNextAttemptAtRoundTrips() {
    val store = SyncJobStore(TestSettings())
    val state = SyncJobState(SyncJobStatus.RUNNING, null, attempt = 0)

    store.write(state)

    store.read() shouldBe state
  }
}
