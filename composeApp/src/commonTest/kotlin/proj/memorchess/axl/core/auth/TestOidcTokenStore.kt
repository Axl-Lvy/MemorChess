package proj.memorchess.axl.core.auth

import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlin.time.Instant
import proj.memorchess.axl.test_util.TestSettings

class TestOidcTokenStore {

  private val expiry = Instant.parse("2026-01-01T00:00:00Z")

  @Test
  fun freshStoreHasNoSession() {
    val store = OidcTokenStore(TestSettings())
    store.currentAccount.value shouldBe null
    store.getAccessToken() shouldBe null
    store.getRefreshToken() shouldBe null
    store.getExpiresAt() shouldBe null
  }

  @Test
  fun saveAndReadFullSession() {
    val store = OidcTokenStore(TestSettings())
    val account = Account(sub = "user-1", name = "Alice")

    store.save("tok-1", "ref-1", expiry, account)

    store.getAccessToken() shouldBe "tok-1"
    store.getRefreshToken() shouldBe "ref-1"
    store.getExpiresAt() shouldBe expiry
    store.currentAccount.value shouldBe account
  }

  @Test
  fun saveWithNullRefreshTokenClearsAPreviouslyStoredOne() {
    val store = OidcTokenStore(TestSettings())
    store.save("tok-1", "ref-1", expiry, Account("user-1", null))

    store.save("tok-2", null, expiry, Account("user-1", null))

    store.getRefreshToken() shouldBe null
  }

  @Test
  fun saveWithNullAccountKeepsThePreviouslyKnownOne() {
    // A refresh response typically carries no id token; the display identity must survive it.
    val store = OidcTokenStore(TestSettings())
    val account = Account(sub = "user-1", name = "Alice")
    store.save("tok-1", "ref-1", expiry, account)

    store.save("tok-2", "ref-2", expiry, null)

    store.currentAccount.value shouldBe account
  }

  @Test
  fun clearRemovesEverything() {
    val store = OidcTokenStore(TestSettings())
    store.save("tok-1", "ref-1", expiry, Account("user-1", "Alice"))

    store.clear()

    store.getAccessToken() shouldBe null
    store.getRefreshToken() shouldBe null
    store.getExpiresAt() shouldBe null
    store.currentAccount.value shouldBe null
  }
}
