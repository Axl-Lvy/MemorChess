package proj.memorchess.axl.ui.layout

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Scaffold
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.window.core.layout.WindowSizeClass

/**
 * Main layout for the application.
 *
 * Always renders a top bar above the content. On narrow screens a bottom bar is also rendered.
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
  Column(modifier = Modifier) {
    topBar()
    Scaffold(
      bottomBar = {
        AnimatedVisibility(
          visible = !isWide,
          enter = slideInVertically(initialOffsetY = { it }),
          exit = slideOutVertically(targetOffsetY = { it }),
        ) {
          bottomBar()
        }
      }
    ) { innerPadding ->
      content(innerPadding)
    }
  }
}
