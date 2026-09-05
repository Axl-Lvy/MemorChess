package proj.memorchess.axl.core.auth

import io.kotest.matchers.shouldBe
import kotlin.test.Test
import proj.memorchess.axl.test_util.TestSettings

class TestPendingOidcRedirectStore {

  @Test
  fun saveThenLoadRoundTrips() {
    val store = PendingOidcRedirectStore(TestSettings())

    store.save(PendingOidcRedirect(state = "s1", codeVerifier = "v1", returnHash = "#settings"))

    store.load() shouldBe
      PendingOidcRedirect(state = "s1", codeVerifier = "v1", returnHash = "#settings")
  }

  @Test
  fun loadWithNothingStoredReturnsNull() {
    val store = PendingOidcRedirectStore(TestSettings())

    store.load() shouldBe null
  }

  @Test
  fun saveOverwritesAnEarlierAbandonedAttempt() {
    val store = PendingOidcRedirectStore(TestSettings())
    store.save(PendingOidcRedirect(state = "old", codeVerifier = "old-v", returnHash = "#old"))

    store.save(PendingOidcRedirect(state = "new", codeVerifier = "new-v", returnHash = "#new"))

    store.load() shouldBe
      PendingOidcRedirect(state = "new", codeVerifier = "new-v", returnHash = "#new")
  }

  @Test
  fun clearRemovesTheRecord() {
    val store = PendingOidcRedirectStore(TestSettings())
    store.save(PendingOidcRedirect(state = "s1", codeVerifier = "v1", returnHash = "#settings"))

    store.clear()

    store.load() shouldBe null
  }

  @Test
  fun emptyReturnHashRoundTrips() {
    val store = PendingOidcRedirectStore(TestSettings())

    store.save(PendingOidcRedirect(state = "s1", codeVerifier = "v1", returnHash = ""))

    store.load() shouldBe PendingOidcRedirect(state = "s1", codeVerifier = "v1", returnHash = "")
  }
}
