package proj.memorchess.axl.ui.layout

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Scaffold
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.window.core.layout.WindowSizeClass
import androidx.window.core.layout.WindowWidthSizeClass

/**
 * Main layout for the application.
 *
 * It consists of a side bar, a bottom bar and a content area.
 */
@Composable
fun MainLayout(
  windowSizeClass: WindowSizeClass = currentWindowAdaptiveInfo().windowSizeClass,
  sideBar: @Composable () -> Unit = {},
  bottomBar: @Composable () -> Unit = {},
  content: @Composable (PaddingValues) -> Unit,
) {
  val isWide by
    remember(windowSizeClass) {
      derivedStateOf { windowSizeClass.windowWidthSizeClass == WindowWidthSizeClass.EXPANDED }
    }
  Row {
    AnimatedVisibility(
      visible = isWide,
      enter = slideInHorizontally(initialOffsetX = { -it }),
      exit = slideOutHorizontally(targetOffsetX = { -it }),
    ) {
      sideBar()
    }
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
    ) {
      content(it)
    }
  }
}
