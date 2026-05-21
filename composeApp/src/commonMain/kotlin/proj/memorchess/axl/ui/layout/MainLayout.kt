package proj.memorchess.axl.ui.layout

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
 *   greater): horizontal [topBar] above the content, no bottom bar.
 * - **Narrow + portrait** (compact width, medium-or-greater height — typical phone portrait):
 *   compact horizontal [topBar] above the content, [bottomBar] visible at the bottom for route
 *   navigation.
 * - **Compact height** (height < `HEIGHT_DP_MEDIUM_LOWER_BOUND` — phone landscape regardless of
 *   width breakpoint, or a desktop window that's been shortened): the [sideBar] slot becomes the
 *   chrome on the left edge and [topBar] / [bottomBar] are not rendered. This trades chrome height
 *   for board height — important because a phone landscape only has ~390.dp of vertical space and
 *   the board has to remain square.
 *
 * @param windowSizeClass Window size class driving wide/narrow + height behaviour.
 * @param topBar Top bar slot; rendered on wide and narrow-portrait screens.
 * @param bottomBar Bottom bar slot; rendered on narrow-portrait screens only.
 * @param sideBar Left side bar slot; rendered on narrow-landscape (compact-height) screens only.
 * @param content Main content slot, receives the scaffold inner padding.
 */
@Composable
fun MainLayout(
  windowSizeClass: WindowSizeClass = currentWindowAdaptiveInfo().windowSizeClass,
  topBar: @Composable () -> Unit = {},
  bottomBar: @Composable () -> Unit = {},
  sideBar: @Composable () -> Unit = {},
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

  if (useSideBar) {
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
  } else {
    Scaffold(
      topBar = topBar,
      bottomBar = {
        AnimatedVisibility(
          visible = !isWide,
          enter = slideInVertically(initialOffsetY = { it }),
          exit = slideOutVertically(targetOffsetY = { it }),
        ) {
          bottomBar()
        }
      },
      // The top and bottom bars consume the system bar insets themselves so we pass empty insets
      // to the scaffold — otherwise the content area would be padded twice.
      contentWindowInsets = WindowInsets(0),
    ) { innerPadding ->
      content(innerPadding)
    }
  }
}
