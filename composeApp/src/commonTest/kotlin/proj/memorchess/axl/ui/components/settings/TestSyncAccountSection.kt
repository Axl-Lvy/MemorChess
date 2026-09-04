package proj.memorchess.axl.ui.components.settings

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import proj.memorchess.axl.core.auth.Account
import proj.memorchess.axl.test_util.TestWithKoin

@OptIn(ExperimentalTestApi::class)
class TestSyncAccountSection : TestWithKoin() {

  private fun runTestFromSetup(block: suspend ComposeUiTest.() -> Unit) = runComposeUiTest {
    koinSetUp()
    try {
      block()
    } finally {
      koinTearDown()
    }
  }

  @Test
  fun signedOutShowsSignInButtonAndNoError() = runTestFromSetup {
    setContent {
      InitializeApp {
        SyncAccountSectionContent(
          account = null,
          pending = false,
          lastError = null,
          onSignIn = {},
          onSignOut = {},
        )
      }
    }

    onNodeWithTag("sync_sign_in_button").assertIsDisplayed()
  }

  @Test
  fun signedInShowsSignOutButtonAndAccountName() = runTestFromSetup {
    setContent {
      InitializeApp {
        SyncAccountSectionContent(
          account = Account(sub = "user-1", name = "Alice"),
          pending = false,
          lastError = null,
          onSignIn = {},
          onSignOut = {},
        )
      }
    }

    onNodeWithTag("sync_sign_out_button").assertIsDisplayed()
    onNodeWithTag("sync_account_name").assertIsDisplayed()
  }

  @Test
  fun signedInWithNoDisplayNameFallsBackToSub() = runTestFromSetup {
    setContent {
      InitializeApp {
        SyncAccountSectionContent(
          account = Account(sub = "user-1", name = null),
          pending = false,
          lastError = null,
          onSignIn = {},
          onSignOut = {},
        )
      }
    }

    onNodeWithTag("sync_account_name").assertIsDisplayed()
  }

  @Test
  fun pendingDisablesSignInButton() = runTestFromSetup {
    setContent {
      InitializeApp {
        SyncAccountSectionContent(
          account = null,
          pending = true,
          lastError = null,
          onSignIn = {},
          onSignOut = {},
        )
      }
    }

    onNodeWithTag("sync_sign_in_button").assertIsDisplayed()
  }

  @Test
  fun errorIsShownWhenPresent() = runTestFromSetup {
    setContent {
      InitializeApp {
        SyncAccountSectionContent(
          account = null,
          pending = false,
          lastError = "Sign in failed: boom",
          onSignIn = {},
          onSignOut = {},
        )
      }
    }

    onNodeWithTag("sync_account_error").assertIsDisplayed()
  }

  @Test
  fun clickingSignInInvokesCallback() = runTestFromSetup {
    var clicked = false
    setContent {
      InitializeApp {
        SyncAccountSectionContent(
          account = null,
          pending = false,
          lastError = null,
          onSignIn = { clicked = true },
          onSignOut = {},
        )
      }
    }

    onNodeWithTag("sync_sign_in_button").performClick()

    check(clicked)
  }

  @Test
  fun clickingSignOutInvokesCallback() = runTestFromSetup {
    var clicked = false
    setContent {
      InitializeApp {
        SyncAccountSectionContent(
          account = Account(sub = "user-1", name = "Alice"),
          pending = false,
          lastError = null,
          onSignIn = {},
          onSignOut = { clicked = true },
        )
      }
    }

    onNodeWithTag("sync_sign_out_button").performClick()

    check(clicked)
  }
}
