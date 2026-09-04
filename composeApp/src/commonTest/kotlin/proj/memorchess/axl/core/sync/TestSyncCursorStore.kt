package proj.memorchess.axl.core.sync

import io.kotest.matchers.shouldBe
import kotlin.test.Test
import proj.memorchess.axl.test_util.TestSettings

class TestSyncCursorStore {

  @Test
  fun freshStoreReadsNull() {
    SyncCursorStore(TestSettings()).read() shouldBe null
  }

  @Test
  fun writeThenReadRoundTrips() {
    val store = SyncCursorStore(TestSettings())
    store.write(42L)
    store.read() shouldBe 42L
  }

  @Test
  fun writingNullClearsIt() {
    val store = SyncCursorStore(TestSettings())
    store.write(42L)
    store.write(null)
    store.read() shouldBe null
  }
}
