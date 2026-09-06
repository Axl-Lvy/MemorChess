package proj.memorchess.axl.ui.layout

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.window.core.layout.WindowSizeClass

/**
 * Main layout for the application. Picks one of three chrome arrangements:
 * - **Wide + tall** (`WIDTH_DP_EXPANDED_LOWER_BOUND` or greater, `HEIGHT_DP_MEDIUM_LOWER_BOUND` or
 *   greater): the [desktopRail] slot becomes the chrome on the left edge; [topBar] and [bottomBar]
 *   are not rendered. This is the true desktop layout — a genuine left rail, not a horizontal bar.
 * - **Narrow + portrait** (compact width, medium-or-greater height — typical phone portrait):
 *   compact horizontal [topBar] above the content, [bottomBar] visible at the bottom for route
 *   navigation.
 * - **Compact height** (height < `HEIGHT_DP_MEDIUM_LOWER_BOUND` — phone landscape regardless of
 *   width breakpoint, or a desktop window that's been shortened): the [sideBar] slot becomes the
 *   chrome on the left edge and [topBar] / [bottomBar] / [desktopRail] are not rendered. This
 *   trades chrome height for board height — important because a phone landscape only has ~390.dp of
 *   vertical space and the board has to remain square. Takes priority over the wide+tall branch: a
 *   phone in landscape can clear the wide-width breakpoint but its height still makes it compact.
 *
 * @param windowSizeClass Window size class driving wide/narrow + height behaviour.
 * @param topBar Top bar slot; rendered on narrow-portrait screens only.
 * @param bottomBar Bottom bar slot; rendered on narrow-portrait screens only.
 * @param sideBar Left side bar slot; rendered on compact-height screens only.
 * @param desktopRail Left rail slot; rendered on wide+tall (true desktop) screens only.
 * @param content Main content slot, receives the scaffold inner padding.
 */
@Composable
fun MainLayout(
  windowSizeClass: WindowSizeClass = currentWindowAdaptiveInfo().windowSizeClass,
  topBar: @Composable () -> Unit = {},
  bottomBar: @Composable () -> Unit = {},
  sideBar: @Composable () -> Unit = {},
  desktopRail: @Composable () -> Unit = {},
  content: @Composable (PaddingValues) -> Unit,
) {
  val isWide by
    remember(windowSizeClass) {
      derivedStateOf {
        windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND)
      }
    }
  val isCompactHeight by
    remember(windowSizeClass) {
      derivedStateOf {
        !windowSizeClass.isHeightAtLeastBreakpoint(WindowSizeClass.HEIGHT_DP_MEDIUM_LOWER_BOUND)
      }
    }
  // Drive the side-bar trigger off height alone. A phone in landscape can clear the wide-width
  // breakpoint (>= 840.dp) but its ~390.dp height still makes a horizontal top bar wasteful.
  val useSideBar = isCompactHeight
  val useDesktopRail = isWide && !isCompactHeight

  when {
    useSideBar -> {
      // Phone-landscape: chrome lives on the left edge, content gets the rest. No top/bottom bars.
      Row(modifier = Modifier.fillMaxSize()) {
        sideBar()
        Scaffold(
          modifier = Modifier.fillMaxSize(),
          // Side bar consumes the system-bar insets; pass empty insets to the scaffold.
          contentWindowInsets = WindowInsets(0),
        ) { innerPadding ->
          content(innerPadding)
        }
      }
    }
    useDesktopRail -> {
      // True desktop: chrome lives on the left edge, content gets the rest. No top/bottom bars,
      // mirroring the compact-height branch's own bar-less arrangement.
      Row(modifier = Modifier.fillMaxSize()) {
        desktopRail()
        Scaffold(
          modifier = Modifier.fillMaxSize(),
          contentWindowInsets = WindowInsets(0),
        ) { innerPadding ->
          content(innerPadding)
        }
      }
    }
    else -> {
      // Narrow-portrait, the only branch left: isWide is always false here (useDesktopRail would
      // have caught it otherwise), so topBar and bottomBar both render plainly, no slide animation.
      Scaffold(
        topBar = topBar,
        bottomBar = bottomBar,
        // The top and bottom bars consume the system bar insets themselves so we pass empty insets
        // to the scaffold — otherwise the content area would be padded twice.
        contentWindowInsets = WindowInsets(0),
      ) { innerPadding ->
        content(innerPadding)
      }
    }
  }
}
