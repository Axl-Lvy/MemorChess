package proj.memorchess.axl.utils

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest
import org.koin.core.component.inject
import proj.memorchess.axl.core.data.online.auth.AuthManager

abstract class TestWithAuthentication : TestFromMainActivity() {

  val authManager by inject<AuthManager>()

  @BeforeTest
  override fun setUp() {
    super.setUp()
    ensureSignedOut()
    assertNull(authManager.user)
  }

  @AfterTest
  fun signOut() {
    ensureSignedOut()
  }

  private fun ensureSignedOut() {
    runTest {
      if (authManager.user != null) {
        authManager.signOut()
        Awaitility.awaitUntilTrue { authManager.user == null }
      }
    }
  }
}
