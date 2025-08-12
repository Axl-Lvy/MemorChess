package proj.memorchess.axl.test_util

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlinx.coroutines.test.runTest
import org.koin.core.component.inject
import proj.memorchess.axl.core.config.generated.Secrets
import proj.memorchess.axl.core.data.online.auth.AuthManager

abstract class TestAuthenticated : TestWithKoin {

  val authManager by inject<AuthManager>()

  @BeforeTest
  override fun setUp() {
    super.setUp()
    ensureSignedOut()
    runTest { authManager.signInFromEmail(Secrets.testUserMail, Secrets.testUserPassword) }
    Awaitility.awaitUntilTrue { authManager.user != null }
  }

  @AfterTest
  override fun tearDown() {
    ensureSignedOut()
    super.tearDown()
  }

  fun ensureSignedOut() {
    runTest {
      if (authManager.user != null) {
        authManager.signOut()
        Awaitility.awaitUntilTrue { authManager.user == null }
      }
    }
  }
}
