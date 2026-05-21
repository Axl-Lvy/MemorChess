package proj.memorchess.axl.ui.components.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import proj.memorchess.axl.ui.components.brand.BrandMark
import proj.memorchess.axl.ui.theme.LocalKineticPalette

/**
 * Kinetic vertical chrome rail. Used on phone-landscape screens where a horizontal top bar would
 * eat too much vertical space.
 *
 * Stacks a [BrandMark] at the top with a [KineticSideNav]-style route picker below, all inside a
 * fixed-width [SIDE_BAR_WIDTH] column. The 1.dp right border + cyan→accent gradient stripe mirror
 * the top bar's gradient underline so the chrome stays visually consistent in both orientations.
 *
 * The bar consumes the status- and navigation-bar insets so its content stays inside the safe area
 * even when the system bars are translucent / edge-to-edge.
 *
 * @param items Route entries to render as nav cells.
 * @param currentRoute Active route label (matched against `item.destination.getLabel()`).
 * @param onSelect Invoked with the tapped item.
 * @param modifier Outer modifier.
 */
@Composable
fun KineticSideBar(
  items: List<NavigationBarItemContent>,
  currentRoute: String,
  onSelect: (NavigationBarItemContent) -> Unit,
  modifier: Modifier = Modifier,
) {
  val palette = LocalKineticPalette.current
  Column(
    modifier =
      modifier
        .width(SIDE_BAR_WIDTH)
        .fillMaxHeight()
        .background(palette.bg2)
        .drawWithContent {
          drawContent()
          val borderPx = 1.dp.toPx()
          drawRect(
            color = palette.line,
            topLeft = Offset(size.width - borderPx, 0f),
            size = Size(borderPx, size.height),
          )
        }
        .windowInsetsPadding(WindowInsets.systemBars),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Box(
      modifier = Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 12.dp),
      contentAlignment = Alignment.Center,
    ) {
      BrandMark(size = 36.dp)
    }
    KineticSideNav(
      items = items,
      currentRoute = currentRoute,
      onSelect = onSelect,
      // The side bar already drew its own background and right border, and applied the system-bar
      // insets — let the nav fill the leftover space without re-doing chrome.
      modifier = Modifier.weight(1f).fillMaxWidth(),
    )
    Spacer(modifier = Modifier.height(8.dp))
  }
}

/**
 * Width of the vertical side bar — matches [KineticSideNav]'s SIDE_WIDTH for visual consistency.
 */
val SIDE_BAR_WIDTH = 72.dp
