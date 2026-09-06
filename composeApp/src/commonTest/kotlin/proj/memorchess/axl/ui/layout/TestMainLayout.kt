package proj.memorchess.axl.ui.layout

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.window.core.layout.WindowSizeClass
import kotlin.test.Test
import proj.memorchess.axl.ui.setKineticContent

/**
 * Pins [MainLayout]'s three mutually exclusive chrome branches against the exact `WindowSizeClass`
 * breakpoints: compact height always wins (the [sideBar] slot), a wide-and-tall window gets the
 * [desktopRail] slot with neither [topBar] nor [bottomBar], and everything else gets
 * [topBar]/[bottomBar].
 */
@OptIn(ExperimentalTestApi::class)
internal class TestMainLayout {

  private fun windowSizeClass(widthDp: Int, heightDp: Int) = WindowSizeClass(widthDp, heightDp)

  @Composable private fun tagged(tag: String) = Box(Modifier.testTag(tag)) { Text(tag) }

  private fun ComposeUiTest.setLayout(widthDp: Int, heightDp: Int) {
    setKineticContent {
      MainLayout(
        windowSizeClass = windowSizeClass(widthDp, heightDp),
        topBar = { tagged("top_bar") },
        bottomBar = { tagged("bottom_bar") },
        sideBar = { tagged("side_bar") },
        desktopRail = { tagged("desktop_rail") },
      ) {
        tagged("content")
      }
    }
  }

  @Test
  fun justBelowWideAndTallGetsTopBarAndBottomBar() = runComposeUiTest {
    setLayout(widthDp = 839, heightDp = 480)

    onNodeWithTag("top_bar").assertExists()
    onNodeWithTag("bottom_bar").assertExists()
    onNodeWithTag("side_bar").assertDoesNotExist()
    onNodeWithTag("desktop_rail").assertDoesNotExist()
    onNodeWithTag("content").assertExists()
  }

  @Test
  fun exactlyWideAndTallGetsTheDesktopRailOnly() = runComposeUiTest {
    setLayout(widthDp = 840, heightDp = 480)

    onNodeWithTag("desktop_rail").assertExists()
    onNodeWithTag("top_bar").assertDoesNotExist()
    onNodeWithTag("bottom_bar").assertDoesNotExist()
    onNodeWithTag("side_bar").assertDoesNotExist()
    onNodeWithTag("content").assertExists()
  }

  @Test
  fun wideButJustBelowTallGetsTheSideBar() = runComposeUiTest {
    setLayout(widthDp = 840, heightDp = 479)

    onNodeWithTag("side_bar").assertExists()
    onNodeWithTag("top_bar").assertDoesNotExist()
    onNodeWithTag("bottom_bar").assertDoesNotExist()
    onNodeWithTag("desktop_rail").assertDoesNotExist()
    onNodeWithTag("content").assertExists()
  }

  @Test
  fun narrowAndJustBelowTallGetsTheSideBar() = runComposeUiTest {
    setLayout(widthDp = 839, heightDp = 479)

    onNodeWithTag("side_bar").assertExists()
    onNodeWithTag("top_bar").assertDoesNotExist()
    onNodeWithTag("bottom_bar").assertDoesNotExist()
    onNodeWithTag("desktop_rail").assertDoesNotExist()
    onNodeWithTag("content").assertExists()
  }

  @Test
  fun aLargeDesktopWindowGetsTheDesktopRailOnly() = runComposeUiTest {
    setLayout(widthDp = 1920, heightDp = 1080)

    onNodeWithTag("desktop_rail").assertExists()
    onNodeWithTag("top_bar").assertDoesNotExist()
    onNodeWithTag("bottom_bar").assertDoesNotExist()
    onNodeWithTag("side_bar").assertDoesNotExist()
    onNodeWithTag("content").assertExists()
  }
}
