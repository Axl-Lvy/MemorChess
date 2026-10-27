package proj.memorchess.axl.test_util

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import kotlin.time.Duration.Companion.seconds

// Mirrors composeApp commonTest helpers, invisible from this module. Keep in sync with
// test_util/TestUtil.kt and ui/util/SemanticMatchers.kt.

/** Maximum time to wait for a UI element to appear. */
val TEST_TIMEOUT = 10.seconds

/** Test tag of the scrollable section list on the settings page. */
const val SETTINGS_SECTION_LIST_TAG = "settingsSectionList"

/** Returns the content description of the tile named [tileName]. */
fun getTileDescription(tileName: String): String {
  return "Tile $tileName"
}

/** Matches nodes whose click action has the given [label]. */
fun hasClickLabel(label: String) =
  SemanticsMatcher("Clickable action with label: $label") {
    it.config.getOrNull(SemanticsActions.OnClick)?.label == label
  }
