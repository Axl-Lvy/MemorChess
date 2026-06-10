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
import proj.memorchess.axl.test_util.TEST_TIMEOUT
import proj.memorchess.axl.ui.pages.SETTINGS_SECTION_LIST_TAG
import proj.memorchess.axl.ui.pages.navigation.Route

/**
 * Tests the Import and Export buttons of the settings page on Android.
 *
 * Opening a native file dialog on Android requires FileKit to be initialized with the activity, so
 * these tests verify that tapping either button starts the picker flow instead of crashing the app.
 * The outgoing picker intent is stubbed with a cancelled result so no real system picker is shown
 * and the flow completes deterministically.
 */
@OptIn(ExperimentalTestApi::class)
class TestImportExport {

  /**
   * The Compose test rule used to interact with the UI. This is automatically initialized by JUnit
   * before tests are executed.
   */
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

  /**
   * Navigates to the settings page, scrolls the section list until the Import/Export section is
   * composed, clicks the given button and checks that the app survives the picker flow.
   *
   * @param buttonTag The test tag of the button to click
   */
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
    // The picker runs in a coroutine on the UI dispatcher: a failure to open the dialog only
    // surfaces once the test synchronizes with the UI again.
    composeTestRule.waitForIdle()
    composeTestRule.onNode(hasTestTag(buttonTag)).assertExists()
  }
}
