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
 * App chrome: [sideBar] on compact height, [desktopRail] on wide windows, else [topBar] and
 * [bottomBar]. Compact height wins, since a landscape phone can be wide but needs a square board.
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
  val useSideBar = isCompactHeight
  val useDesktopRail = isWide && !isCompactHeight

  when {
    useSideBar -> {
      Row(modifier = Modifier.fillMaxSize()) {
        sideBar()
        Scaffold(
          modifier = Modifier.fillMaxSize(),
          // The side bar consumes the system bar insets.
          contentWindowInsets = WindowInsets(0),
        ) { innerPadding ->
          content(innerPadding)
        }
      }
    }
    useDesktopRail -> {
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
      Scaffold(
        topBar = topBar,
        bottomBar = bottomBar,
        // The bars consume the system bar insets, so the content is not padded twice.
        contentWindowInsets = WindowInsets(0),
      ) { innerPadding ->
        content(innerPadding)
      }
    }
  }
}
