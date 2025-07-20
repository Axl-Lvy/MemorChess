package proj.memorchess.axl.online

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import proj.memorchess.axl.core.config.AUTH_REFRESH_TOKEN_SETTINGS
import proj.memorchess.axl.core.config.KEEP_LOGGED_IN_SETTING
import proj.memorchess.axl.core.config.generated.Secrets
import proj.memorchess.axl.test_util.TEST_TIMEOUT
import proj.memorchess.axl.utils.Awaitility
import proj.memorchess.axl.utils.TestWithAuthentication

@OptIn(ExperimentalTestApi::class)
class TestAuthentication : TestWithAuthentication() {

  @Test
  fun testSignInButtonShowsCorrectStateWhenSignedOut() {
    // Initially user should be signed out
    assertNodeWithTagExists("sign_in_button")
    assertNodeWithTextDoesNotExists("Signed in")
  }

  @Test
  fun testSignInDialogCanBeCanceled() {
    // Open the dialog
    assertNodeWithTagExists("sign_in_button").performClick()
    assertNodeWithTagExists("sign_in_confirmation_button")

    // Cancel the dialog
    assertNodeWithTextExists("Cancel").performClick()

    // Verify dialog is closed and we're back to initial state
    assertNodeWithTextDoesNotExists("Email")
    assertNodeWithTextDoesNotExists("Password")
    assertNodeWithTextExists("Sign in")
    assertTrue(authManager.user == null)
  }

  @Test
  fun testSuccessfulSignInWithValidCredentials() {
    // Open sign in dialog
    assertNodeWithTagExists("sign_in_button").performClick()

    // Enter valid credentials from Secrets
    waitUntilNodeExists(hasText("Email")).performTextInput(Secrets.testUserMail)
    waitUntilNodeExists(hasText("Password")).performTextInput(Secrets.testUserPassword)

    // Submit the form
    assertNodeWithTagExists("sign_in_confirmation_button").performClick()

    // Wait for dialog to close and verify signed in state
    composeTestRule.waitUntilDoesNotExist(hasText("Email"), TEST_TIMEOUT.inWholeMilliseconds)
    assertNodeWithTextExists("Signed in")
    assertNodeWithTextDoesNotExists("Sign in")
    assertFalse { KEEP_LOGGED_IN_SETTING.getValue() }

    // Verify user is actually signed in through AuthManager
    assertTrue(authManager.user != null)
  }

  @Test
  fun testKeepSession() {
    testSuccessfulSignInWithValidCredentials()
    assertNodeWithTextExists("Stay signed in?")
    assertNodeWithTextExists("Yes").performClick()
    Awaitility.awaitUntilTrue(TEST_TIMEOUT) { KEEP_LOGGED_IN_SETTING.getValue() }
    Awaitility.awaitUntilTrue(TEST_TIMEOUT) { AUTH_REFRESH_TOKEN_SETTINGS.getValue().isNotEmpty() }
  }

  @Test
  fun testDoNotKeepSession() {
    testSuccessfulSignInWithValidCredentials()
    assertNodeWithTextExists("Stay signed in?")
    assertNodeWithTextExists("No").performClick()
    Awaitility.awaitUntilTrue(TEST_TIMEOUT) { !KEEP_LOGGED_IN_SETTING.getValue() }
    Awaitility.awaitUntilTrue(TEST_TIMEOUT) { AUTH_REFRESH_TOKEN_SETTINGS.getValue().isEmpty() }
  }

  @Test
  fun testFailedSignInWithInvalidCredentials() {
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
    assertTrue(authManager.user == null)
  }

  @Test
  fun testSignOut() {
    // First sign in
    runTest { authManager.signInFromEmail(Secrets.testUserMail, Secrets.testUserPassword) }

    // Navigate away and back to refresh the UI
    goToExplore()
    goToSettings()

    // Verify we still see "Signed in" and try to click it
    assertNodeWithTagExists("sign_in_button").performClick()

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
