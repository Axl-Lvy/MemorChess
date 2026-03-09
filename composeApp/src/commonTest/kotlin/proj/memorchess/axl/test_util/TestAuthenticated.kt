package proj.memorchess.axl.test_util

import io.kotest.assertions.nondeterministic.eventually
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import org.koin.core.component.inject
import proj.memorchess.axl.core.config.generated.Secrets
import proj.memorchess.axl.core.data.online.auth.AuthManager

abstract class TestAuthenticated : TestWithKoin() {

  val authManager by inject<AuthManager>()

  override suspend fun setUp() {
    ensureSignedOut()
    authManager.signInFromEmail(Secrets.testUserMail, Secrets.testUserPassword)
    eventually(TEST_TIMEOUT) { assertNotNull(authManager.user) }
  }

  override suspend fun tearDown() {
    ensureSignedOut()
  }

  suspend fun ensureSignedOut() {
    if (authManager.user != null) {
      authManager.signOut()
      eventually(TEST_TIMEOUT) { assertNull(authManager.user) }
    }
  }
}
