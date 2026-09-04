package proj.memorchess.axl.core.auth

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlin.test.Test
import org.koin.core.component.inject
import proj.memorchess.axl.test_util.TestWithKoin

/**
 * Verifies the DI graph actually resolves an [AuthProvider], catching a broken Koin binding
 * (missing dependency, wrong scope) that a unit test constructing [OidcSignInController] directly
 * would never see.
 */
class TestAuthProviderWiring : TestWithKoin() {

  private val authProvider: AuthProvider by inject()

  @Test
  fun authProviderResolvesToOidcSignInControllerWithNoStoredSession() = test {
    authProvider.shouldBeInstanceOf<OidcSignInController>()
    authProvider.currentAccount.value shouldBe null
  }
}
