package proj.memorchess.axl.core.data.online

import androidx.compose.ui.test.*
import kotlin.test.*
import kotlinx.coroutines.test.runTest
import org.koin.core.component.inject
import proj.memorchess.axl.core.config.AUTH_REFRESH_TOKEN_SETTINGS
import proj.memorchess.axl.core.config.KEEP_LOGGED_IN_SETTING
import proj.memorchess.axl.core.config.generated.Secrets
import proj.memorchess.axl.core.data.online.auth.AuthManager
import proj.memorchess.axl.test_util.Awaitility
import proj.memorchess.axl.test_util.TEST_TIMEOUT
import proj.memorchess.axl.test_util.TestWithKoin
import proj.memorchess.axl.ui.assertNodeWithTagExists
import proj.memorchess.axl.ui.assertNodeWithTextDoesNotExists
import proj.memorchess.axl.ui.assertNodeWithTextExists
import proj.memorchess.axl.ui.pages.Settings
import proj.memorchess.axl.ui.waitUntilNodeExists

@OptIn(ExperimentalTestApi::class)
class TestAuthentication : TestWithKoin {

  val authManager by inject<AuthManager>()

  fun runTestFromSetup(block: ComposeUiTest.() -> Unit) {
    runComposeUiTest {
      setContent { initializeApp { Settings() } }
      assertNodeWithTagExists("sign_in_button").performScrollTo()
      block()
    }
  }

  @BeforeTest
  override fun setUp() {
    super.setUp()
    runTest {
      signOut() // Ensure we start with a clean state
    }
  }

  @AfterTest
  override fun tearDown() {
    super.tearDown()
    runTest {
      signOut() // Clean up after tests
    }
  }

  private fun signOut() {
    runTest {
      if (authManager.user != null) {
        authManager.signOut()
        Awaitility.awaitUntilTrue { authManager.user == null }
      }
    }
  }

  @Test
  fun testSignInButtonShowsCorrectStateWhenSignedOut() = runTestFromSetup {
    // Initially user should be signed out
    assertNodeWithTagExists("sign_in_button")
    assertNodeWithTextDoesNotExists("Signed in")
  }

  @Test
  fun testSignInDialogCanBeCanceled() = runTestFromSetup {
    // Open the dialog
    assertNodeWithTagExists("sign_in_button").performClick()
    assertNodeWithTagExists("sign_in_confirmation_button")

    // Cancel the dialog
    assertNodeWithTextExists("Cancel").performClick()

    // Verify dialog is closed and we're back to initial state
    assertNodeWithTextDoesNotExists("Email")
    assertNodeWithTextDoesNotExists("Password")
    assertNodeWithTextExists("Sign in")
    assertNull(authManager.user)
  }

  @Test fun testSuccessfulSignInWithValidCredentials() = runTestFromSetup { signInWorkflow() }

  private fun ComposeUiTest.signInWorkflow() {
    // Open sign in dialog
    assertNodeWithTagExists("sign_in_button").performClick()

    // Enter valid credentials from Secrets
    waitUntilNodeExists(hasText("Email")).performTextInput(Secrets.testUserMail)
    waitUntilNodeExists(hasText("Password")).performTextInput(Secrets.testUserPassword)

    // Submit the form
    assertNodeWithTagExists("sign_in_confirmation_button").performClick()

    // Wait for dialog to close and verify signed in state
    waitUntilDoesNotExist(hasText("Email"), TEST_TIMEOUT.inWholeMilliseconds)
    assertNodeWithTextExists("Signed in")
    assertNodeWithTextDoesNotExists("Sign in")
    assertFalse { KEEP_LOGGED_IN_SETTING.getValue() }

    // Verify user is actually signed in through AuthManager
    assertNotNull(authManager.user)
  }

  @Test
  fun testKeepSession() = runTestFromSetup {
    signInWorkflow()
    assertNodeWithTextExists("Stay signed in?")
    assertNodeWithTextExists("Yes").performClick()
    Awaitility.awaitUntilTrue(TEST_TIMEOUT) { KEEP_LOGGED_IN_SETTING.getValue() }
    Awaitility.awaitUntilTrue(TEST_TIMEOUT) { AUTH_REFRESH_TOKEN_SETTINGS.getValue().isNotEmpty() }
  }

  @Test
  fun testDoNotKeepSession() = runTestFromSetup {
    signInWorkflow()
    assertNodeWithTextExists("Stay signed in?")
    assertNodeWithTextExists("No").performClick()
    Awaitility.awaitUntilTrue(TEST_TIMEOUT) { !KEEP_LOGGED_IN_SETTING.getValue() }
    Awaitility.awaitUntilTrue(TEST_TIMEOUT) { AUTH_REFRESH_TOKEN_SETTINGS.getValue().isEmpty() }
  }

  @Test
  fun testKeepSessionDialogNotReopens() = runTestFromSetup {
    signInWorkflow()
    assertNodeWithTextExists("Stay signed in?")
    assertNodeWithTextExists("Yes").performClick()
    Awaitility.awaitUntilTrue(TEST_TIMEOUT) { KEEP_LOGGED_IN_SETTING.getValue() }
    assertNodeWithTextExists("DARK").performClick()
    assertNodeWithTextExists("LIGHT").performClick()
    assertNodeWithTextDoesNotExists("Stay signed in?")
  }

  @Test
  fun testFailedSignInWithInvalidCredentials() = runTestFromSetup {
    // Open sign in dialog
    assertNodeWithTagExists("sign_in_button").performClick()

    // Enter invalid credentials
    waitUntilNodeExists(hasText("Email")).performTextInput("invalid@example.com")
    waitUntilNodeExists(hasText("Password")).performTextInput("wrongpassword")

    // Submit the form
    assertNodeWithTagExists("sign_in_confirmation_button").performClick()

    // Verify error message appears
    assertNodeWithTextExists("Invalid email or password")

    // Verify dialog stays open
    assertNodeWithTextExists("Email")
    assertNodeWithTextExists("Password")

    // Verify user is still signed out
    assertNull(authManager.user)
  }

  @Test
  fun testSignOut() = runTestFromSetup {
    // First sign in
    runTest { authManager.signInFromEmail(Secrets.testUserMail, Secrets.testUserPassword) }

    // Verify we still see "Signed in" and try to click it
    assertNodeWithTagExists("sign_in_button").performScrollTo().performClick()

    // Verify dialog does not appear (button should be disabled)
    assertNodeWithTextDoesNotExists("Email")
    assertNodeWithTextDoesNotExists("Password")
    assertNodeWithTextExists("Are you sure you want to sign out?")
    assertNodeWithTextExists("Cancel").performClick()

    // Verify we still see "Signed in" and try to click it
    assertNodeWithTagExists("sign_in_button").performClick()

    // Verify dialog does not appear (button should be disabled)
    assertNodeWithTextDoesNotExists("Email")
    assertNodeWithTextDoesNotExists("Password")
    assertNodeWithTextExists("Are you sure you want to sign out?")
    assertNodeWithTextExists("Sign Out").performClick()
    Awaitility.awaitUntilTrue(TEST_TIMEOUT) { authManager.user == null }
  }
}
