package proj.memorchess.axl.utils

import androidx.compose.ui.test.performScrollTo
import kotlin.getValue
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.koin.core.component.inject
import proj.memorchess.axl.core.data.online.auth.AuthManager

abstract class TestWithAuthentication : AUiTestFromMainActivity() {

  val authManager by inject<AuthManager>()

  @BeforeTest
  override fun setUp() {
    super.setUp()
    // Ensure user is signed out before each test
    runTest { authManager.signOut() }
    goToSettings()
    assertNodeWithTagExists("sign_in_button").performScrollTo()
    assertTrue(authManager.user == null)
  }

  @AfterTest
  fun signOut() {
    runTest { authManager.signOut() }
  }
}
