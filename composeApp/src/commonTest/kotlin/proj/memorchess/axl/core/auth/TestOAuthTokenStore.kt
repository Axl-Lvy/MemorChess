package proj.memorchess.axl.core.auth

import io.kotest.matchers.shouldBe
import kotlin.test.Test
import proj.memorchess.axl.test_util.TestSettings

class TestOAuthTokenStore {

  @Test
  fun freshStoreHasNullAccount() {
    val store = OAuthTokenStore(TestSettings())
    store.account.value shouldBe null
    store.getToken() shouldBe null
  }

  @Test
  fun saveAndReadToken() {
    val store = OAuthTokenStore(TestSettings())
    store.save("tok123", username = "alice")
    store.getToken() shouldBe "tok123"
    store.account.value shouldBe LichessAccount(token = "tok123", username = "alice")
  }

  @Test
  fun setUsernamePreservesToken() {
    val store = OAuthTokenStore(TestSettings())
    store.save("tok123", username = null)
    store.setUsername("alice")
    store.getToken() shouldBe "tok123"
    store.account.value?.username shouldBe "alice"
  }

  @Test
  fun setUsernameWithoutTokenIsNoop() {
    val store = OAuthTokenStore(TestSettings())
    store.setUsername("alice")
    store.getToken() shouldBe null
    store.account.value shouldBe null
  }

  @Test
  fun clearRemovesEverything() {
    val store = OAuthTokenStore(TestSettings())
    store.save("tok123", username = "alice")
    store.clear()
    store.getToken() shouldBe null
    store.account.value shouldBe null
  }

  @Test
  fun reloadsExistingValuesFromSettings() {
    val settings = TestSettings()
    OAuthTokenStore(settings).save("tok123", username = "alice")
    val reopened = OAuthTokenStore(settings)
    reopened.getToken() shouldBe "tok123"
    reopened.account.value shouldBe LichessAccount("tok123", "alice")
  }
}
