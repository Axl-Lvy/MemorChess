package proj.memorchess.axl.core.auth

import io.kotest.matchers.shouldBe
import kotlin.test.Test
import org.koin.core.component.inject
import proj.memorchess.axl.test_util.TestWithKoin

/**
 * Catches a broken Koin binding for [AuthProvider] that direct construction would miss. Not pinned
 * to a concrete type, since wasmJs binds a different implementation.
 */
class TestAuthProviderWiring : TestWithKoin() {

  private val authProvider: AuthProvider by inject()

  @Test
  fun authProviderResolvesWithNoStoredSession() = test {
    authProvider.currentAccount.value shouldBe null
  }
}
