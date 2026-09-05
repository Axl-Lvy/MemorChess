package proj.memorchess.axl.core.auth

import io.kotest.matchers.shouldBe
import kotlin.test.Test
import org.koin.core.component.inject
import proj.memorchess.axl.test_util.TestWithKoin

/**
 * Verifies the DI graph actually resolves an [AuthProvider], catching a broken Koin binding
 * (missing dependency, wrong scope) that a unit test constructing [OidcSignInController] directly
 * would never see. Not pinned to a concrete type: [getPlatformSpecificSyncAuthProvider] returns a
 * different implementation on wasmJs ([OidcRedirectSignInController]) than on other platforms.
 */
class TestAuthProviderWiring : TestWithKoin() {

  private val authProvider: AuthProvider by inject()

  @Test
  fun authProviderResolvesWithNoStoredSession() = test {
    authProvider.currentAccount.value shouldBe null
  }
}
