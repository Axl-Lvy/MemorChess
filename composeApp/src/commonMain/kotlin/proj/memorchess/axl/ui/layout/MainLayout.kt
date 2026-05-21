package proj.memorchess.axl.ui.layout

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.Scaffold
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.window.core.layout.WindowSizeClass

/**
 * Main layout for the application.
 *
 * Always renders a top bar above the content. On narrow screens a bottom bar is also rendered. Both
 * top and bottom bars are slotted into a Material 3 [Scaffold] so the system bars (status bar,
 * gesture nav) automatically get the right insets — the topBar is no longer drawn under the Android
 * status bar.
 *
 * @param windowSizeClass Window size class driving wide/narrow behaviour. Defaults to the current
 *   window's adaptive info.
 * @param topBar Top bar slot; always visible.
 * @param bottomBar Bottom bar slot; only visible on narrow screens.
 * @param content Main content slot, receives the scaffold inner padding.
 */
@Composable
fun MainLayout(
  windowSizeClass: WindowSizeClass = currentWindowAdaptiveInfo().windowSizeClass,
  topBar: @Composable () -> Unit = {},
  bottomBar: @Composable () -> Unit = {},
  content: @Composable (PaddingValues) -> Unit,
) {
  val isWide by
    remember(windowSizeClass) {
      derivedStateOf {
        windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND)
      }
    }
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
    // The top and bottom bars consume the system bar insets themselves via
    // `windowInsetsPadding(statusBars/navigationBars)` so we pass empty insets to the scaffold —
    // otherwise the content area would be padded twice.
    contentWindowInsets = WindowInsets(0),
  ) { innerPadding ->
    content(innerPadding)
  }
}
