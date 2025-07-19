package proj.memorchess.axl.online

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import proj.memorchess.axl.core.config.generated.Secrets
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
    composeTestRule.waitUntilDoesNotExist(hasText("Email"))
    assertNodeWithTextExists("Signed in")
    assertNodeWithTextDoesNotExists("Sign in")

    // Verify user is actually signed in through AuthManager
    assertTrue(authManager.user != null)
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
    testSuccessfulSignInWithValidCredentials()
    runTest { authManager.signOut() }
    assert(authManager.user == null)
  }

  @Test
  fun testSignInButtonIsDisabledWhenSignedIn() {
    // First sign in
    testSuccessfulSignInWithValidCredentials()

    // Navigate away and back to refresh the UI
    goToExplore()
    goToSettings()

    // Verify we still see "Signed in" and try to click it
    assertNodeWithTagExists("sign_in_button").performClick()

    // Verify dialog does not appear (button should be disabled)
    assertNodeWithTextDoesNotExists("Email")
    assertNodeWithTextDoesNotExists("Password")
    assertTrue(authManager.user != null)
  }
}
