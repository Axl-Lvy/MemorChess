package proj.memorchess.axl.ui.components.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import proj.memorchess.axl.ui.theme.LocalKineticPalette
import proj.memorchess.axl.ui.theme.LocalKineticTypography

/** Bottom border thickness shared by the bar's edge stroke. */
private val BORDER_PX = 1.dp

/**
 * Thickness of the active-indicator bar (matches `.bottomnav a.active::before { height: 2px }`).
 */
private val INDICATOR_THICKNESS = 2.dp

/** Cell icon target size (CSS `.nav-btn svg { width: 20px }`, bumped per spec to 22.dp). */
private val ICON_SIZE = 22.dp

/** Vertical gap between icon and label (CSS uses `gap: 3px`; spec calls for 6.dp). */
private val ICON_LABEL_SPACER = 6.dp

/** Total bar height — matches `.bottomnav { height: 70px }`. */
private val BAR_HEIGHT = 70.dp

/** Side bar width on wide screens. */
private val SIDE_WIDTH = 72.dp

/** Cell height inside the side nav (matches the side width for a square footprint). */
private val SIDE_CELL_HEIGHT = 72.dp

/**
 * Returns `true` when [currentRoute] resolves to the same destination as [item]. Mirrors the
 * convention used by the legacy nav bars (`currentRoute == item.destination.getLabel()`).
 */
private fun isActive(item: NavigationBarItemContent, currentRoute: String): Boolean =
  currentRoute == item.destination.getLabel()

/**
 * Renders [item]'s icon constrained to [ICON_SIZE] and tinted with [tint]. The enum's icon lambda
 * relies on `LocalContentColor` (it builds plain [androidx.compose.material3.Icon] composables), so
 * the tint flows through via [CompositionLocalProvider].
 */
@Composable
private fun NavCellIcon(item: NavigationBarItemContent, tint: Color) {
  Box(modifier = Modifier.size(ICON_SIZE), contentAlignment = Alignment.Center) {
    CompositionLocalProvider(LocalContentColor provides tint) { item.icon() }
  }
}

/**
 * Kinetic mobile bottom navigation bar.
 *
 * Mirrors `.bottomnav` from `design-proposals/kinetic-base.css`: a full-width row with `bg2`
 * background, 1.dp `line` top border, and four equal-width cells stacking an icon over a 9sp
 * uppercase mono label. The active cell paints a 2.dp `accent` bar across the inner 28%→72% of its
 * top edge (CSS `.bottomnav a.active::before`).
 *
 * @param items Navigation entries; rendered in the order supplied.
 * @param currentRoute The current route label; compared against [NavigationBarItemContent]'s
 *   destination label to determine the active cell.
 * @param onSelect Invoked when the user taps a cell.
 * @param modifier Optional layout modifier.
 */
@Composable
fun KineticBottomNav(
  items: List<NavigationBarItemContent>,
  currentRoute: String,
  onSelect: (NavigationBarItemContent) -> Unit,
  modifier: Modifier = Modifier,
) {
  val palette = LocalKineticPalette.current
  val typography = LocalKineticTypography.current
  Row(
    modifier =
      modifier.fillMaxWidth().height(BAR_HEIGHT).background(palette.bg2).drawBehind {
        // Top border (1.dp line) painted along the bar's top edge.
        val strokePx = BORDER_PX.toPx()
        drawRect(
          color = palette.line,
          topLeft = androidx.compose.ui.geometry.Offset(0f, 0f),
          size = androidx.compose.ui.geometry.Size(size.width, strokePx),
        )
      },
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceEvenly,
  ) {
    items.forEach { item ->
      val active = isActive(item, currentRoute)
      val tint = if (active) palette.accent else palette.ink3
      Column(
        modifier =
          Modifier.weight(1f)
            .fillMaxHeight()
            .clickable(enabled = true, onClick = { onSelect(item) })
            .drawBehind {
              if (active) {
                // Active indicator: 2.dp accent bar across 28%→72% of the cell's top edge,
                // painted just below the 1.dp top border so it sits inside the cell.
                val thicknessPx = INDICATOR_THICKNESS.toPx()
                val borderPx = BORDER_PX.toPx()
                val left = size.width * 0.28f
                val right = size.width * 0.72f
                drawRect(
                  color = palette.accent,
                  topLeft = androidx.compose.ui.geometry.Offset(left, borderPx),
                  size = androidx.compose.ui.geometry.Size(right - left, thicknessPx),
                )
              }
            },
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        NavCellIcon(item, tint)
        Spacer(modifier = Modifier.height(ICON_LABEL_SPACER))
        Text(
          text = item.destination.getLabel().uppercase(),
          style = typography.monoSm.copy(color = tint),
        )
      }
    }
  }
}

/**
 * Kinetic wide-screen side navigation rail.
 *
 * Vertical counterpart to [KineticBottomNav]: a fixed-width [SIDE_WIDTH] column with `bg2`
 * background and a 1.dp `line` right border. Each cell is [SIDE_CELL_HEIGHT] tall, stacking the
 * icon over a 9sp uppercase mono label. Cells are top-aligned; the remaining space is pushed to the
 * bottom via a weighted spacer so future extras (e.g. a footer chip) can be slotted in cleanly.
 *
 * Active indicator: a 2.dp `accent` vertical bar painted on the LEFT edge of the active cell,
 * spanning the inner 28%→72% of its height (just inside the bar's right border).
 *
 * @param items Navigation entries; rendered in the order supplied.
 * @param currentRoute The current route label; compared against [NavigationBarItemContent]'s
 *   destination label to determine the active cell.
 * @param onSelect Invoked when the user taps a cell.
 * @param modifier Optional layout modifier.
 */
@Composable
fun KineticSideNav(
  items: List<NavigationBarItemContent>,
  currentRoute: String,
  onSelect: (NavigationBarItemContent) -> Unit,
  modifier: Modifier = Modifier,
) {
  val palette = LocalKineticPalette.current
  val typography = LocalKineticTypography.current
  Column(
    modifier =
      modifier.width(SIDE_WIDTH).fillMaxHeight().background(palette.bg2).drawBehind {
        // Right border (1.dp line) painted along the rail's right edge.
        val strokePx = BORDER_PX.toPx()
        drawRect(
          color = palette.line,
          topLeft = androidx.compose.ui.geometry.Offset(size.width - strokePx, 0f),
          size = androidx.compose.ui.geometry.Size(strokePx, size.height),
        )
      },
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    items.forEach { item ->
      val active = isActive(item, currentRoute)
      val tint = if (active) palette.accent else palette.ink3
      Column(
        modifier =
          Modifier.fillMaxWidth()
            .height(SIDE_CELL_HEIGHT)
            .clickable(enabled = true, onClick = { onSelect(item) })
            .drawBehind {
              if (active) {
                // Active indicator: 2.dp accent vertical bar across 28%→72% of the cell's
                // left edge.
                val thicknessPx = INDICATOR_THICKNESS.toPx()
                val top = size.height * 0.28f
                val bottom = size.height * 0.72f
                drawRect(
                  color = palette.accent,
                  topLeft = androidx.compose.ui.geometry.Offset(0f, top),
                  size = androidx.compose.ui.geometry.Size(thicknessPx, bottom - top),
                )
              }
            },
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        NavCellIcon(item, tint)
        Spacer(modifier = Modifier.height(ICON_LABEL_SPACER))
        Text(
          text = item.destination.getLabel().uppercase(),
          style = typography.monoSm.copy(color = tint),
        )
      }
    }
    Spacer(modifier = Modifier.weight(1f))
  }
}
