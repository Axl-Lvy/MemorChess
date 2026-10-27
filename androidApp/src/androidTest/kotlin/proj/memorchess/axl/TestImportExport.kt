package proj.memorchess.axl

import android.app.Activity
import android.app.Instrumentation
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers
import kotlin.test.Test
import org.junit.After
import org.junit.Before
import org.junit.Rule
import proj.memorchess.axl.test_util.SETTINGS_SECTION_LIST_TAG
import proj.memorchess.axl.test_util.TEST_TIMEOUT
import proj.memorchess.axl.ui.pages.navigation.Route

/**
 * Checks that the settings Import and Export buttons start the file picker without crashing. Picker
 * intents are stubbed with a cancelled result so no real system picker is shown.
 */
@OptIn(ExperimentalTestApi::class)
class TestImportExport {

  @get:Rule val composeTestRule = createAndroidComposeRule<MainActivity>()

  @Before
  fun stubFilePickerIntents() {
    Intents.init()
    Intents.intending(IntentMatchers.anyIntent())
      .respondWith(Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null))
  }

  @After
  fun releaseIntents() {
    Intents.release()
  }

  @Test
  fun importButtonOpensFilePicker() {
    clickImportExportButton("importButton")
  }

  @Test
  fun exportButtonOpensFileSaver() {
    clickImportExportButton("exportButton")
  }

  /** Clicks the settings button tagged [buttonTag] and checks the app survives the picker flow. */
  private fun clickImportExportButton(buttonTag: String) {
    val settingsNavTag = "bottom_navigation_bar_item_${Route.SettingsRoute.getLabel()}"
    composeTestRule.waitUntilAtLeastOneExists(
      hasTestTag(settingsNavTag),
      TEST_TIMEOUT.inWholeMilliseconds,
    )
    composeTestRule.onNode(hasTestTag(settingsNavTag)).performClick()
    composeTestRule.waitUntilAtLeastOneExists(
      hasTestTag(SETTINGS_SECTION_LIST_TAG),
      TEST_TIMEOUT.inWholeMilliseconds,
    )
    composeTestRule
      .onNode(hasTestTag(SETTINGS_SECTION_LIST_TAG))
      .performScrollToNode(hasTestTag(buttonTag))
    composeTestRule.onNode(hasTestTag(buttonTag)).performClick()
    // The picker runs on the UI dispatcher, so a failure only surfaces once the test syncs again.
    composeTestRule.waitForIdle()
    composeTestRule.onNode(hasTestTag(buttonTag)).assertExists()
  }
}
