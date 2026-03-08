package proj.memorchess.axl.core.config

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test
import proj.memorchess.axl.test_util.TestWithKoin
import proj.memorchess.axl.ui.assertNodeWithTagDoesNotExists
import proj.memorchess.axl.ui.assertNodeWithTagExists
import proj.memorchess.axl.ui.assertNodeWithTextDoesNotExists
import proj.memorchess.axl.ui.pages.Settings

@OptIn(ExperimentalTestApi::class)
class TestFeatureFlags : TestWithKoin {

  @Test
  fun testAuthUiHiddenWhenAuthDisabled() = runComposeUiTest {
    FeatureFlags.isAuthEnabled = false
    setContent { InitializeApp { Settings() } }

    assertNodeWithTagExists("resetConfigButton")
    assertNodeWithTagDoesNotExists("sign_in_button")
    assertNodeWithTextDoesNotExists("Database Synchronisation")
  }

  @Test
  fun testAuthUiShownWhenAuthEnabled() = runComposeUiTest {
    FeatureFlags.isAuthEnabled = true
    setContent { InitializeApp { Settings() } }

    assertNodeWithTagExists("sign_in_button")
  }
}
