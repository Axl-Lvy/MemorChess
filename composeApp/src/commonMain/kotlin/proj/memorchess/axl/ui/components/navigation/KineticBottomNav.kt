package proj.memorchess.axl.ui.components.navigation

import androidx.compose.animation.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import proj.memorchess.axl.ui.theme.KineticMotion
import proj.memorchess.axl.ui.theme.KineticPalette
import proj.memorchess.axl.ui.theme.LocalKineticPalette
import proj.memorchess.axl.ui.theme.LocalKineticTypography

/** Thickness of the bar's top line — the mockup's `border-top: 1.5px`. */
private val BAR_BORDER = 1.5.dp

/** Cell icon target size (mockup: `width: 23px`). */
private val ICON_SIZE = 23.dp

/** Vertical gap between icon and label (mockup: `gap: 4px`). */
private val ICON_LABEL_SPACER = 4.dp

/** Total bar height, bottom padding included — the mockup's `height: 80px`. */
private val BAR_HEIGHT = 80.dp

/** Horizontal inset of the cell row (mockup: `padding: 0 6px 10px`). */
private val BAR_HORIZONTAL_PADDING = 6.dp

/** Bottom inset of the cell row, taken out of [BAR_HEIGHT]. */
private val BAR_BOTTOM_PADDING = 10.dp

/** Corner radius of the active cell's filled pill. */
private val PILL_RADIUS = 14.dp

/** Vertical padding inside the pill, applied to active and inactive cells alike. */
private val PILL_VERTICAL_PADDING = 5.dp

/** Horizontal padding inside the pill, applied to active and inactive cells alike. */
private val PILL_HORIZONTAL_PADDING = 16.dp

/** Label size (mockup: `font: 900 9.5px Nunito`). */
private val LABEL_SIZE = 9.5.sp

/**
 * Returns `true` when [currentRoute] resolves to the same destination as [item]. The route fed in
 * from the back stack is the destination's serial name (lower-case, e.g. `"training"`) while
 * `getLabel()` is capitalised (`"Training"`), so the match is case-insensitive.
 */
internal fun isActive(item: NavigationBarItemContent, currentRoute: String): Boolean =
  currentRoute.equals(item.destination.getLabel(), ignoreCase = true)

/**
 * Resolved look of one navigation cell, shared by the bottom bar and the desktop side rail.
 *
 * [pill] is the cell's fill. When inactive it is [KineticPalette.actionDim] at **alpha 0** rather
 * than [Color.Transparent] — the latter is black at alpha 0, and Compose interpolates colours
 * through Oklab, so fading the violet pill to it would drag the mid-animation frames through grey.
 * Keeping the hue fixed and animating only the alpha gives a clean fade, and still paints nothing
 * where the fill is applied statically.
 */
@Immutable
internal data class KineticNavCellStyle(
  val pill: Color,
  val content: Color,
  val labelWeight: FontWeight,
)

/** Look of a nav cell in [palette], for the active or inactive state. */
internal fun kineticNavCellStyle(palette: KineticPalette, active: Boolean): KineticNavCellStyle =
  if (active) KineticNavCellStyle(palette.actionDim, palette.action, FontWeight.Black)
  else KineticNavCellStyle(palette.actionDim.copy(alpha = 0f), palette.ink3, FontWeight.ExtraBold)

/**
 * Renders [item]'s icon constrained to [size] and tinted with [tint]. The enum's icon lambda relies
 * on `LocalContentColor` (it builds plain [androidx.compose.material3.Icon] composables), so the
 * tint flows through via [CompositionLocalProvider].
 */
@Composable
internal fun NavCellIcon(item: NavigationBarItemContent, tint: Color, size: Dp = ICON_SIZE) {
  Box(modifier = Modifier.size(size), contentAlignment = Alignment.Center) {
    CompositionLocalProvider(LocalContentColor provides tint) { item.icon() }
  }
}

/**
 * Kinetic mobile bottom navigation bar.
 *
 * A full-width, edge-anchored row on `panel` with a 1.5.dp `line` top border and four equal-width
 * cells stacking an icon over a 9.5sp uppercase label. The active cell paints a filled 14.dp
 * `actionDim` pill behind its icon and inks both icon and label with `action`; selection is never a
 * colour-only change. Inactive cells keep the identical pill padding, so nothing shifts on
 * selection.
 *
 * The pill's fade is held in an [Animatable] read **inside `drawBehind`**, so it runs in the draw
 * phase with no recomposition and the motion spec is built at the launch site rather than on every
 * composition.
 *
 * @param items Navigation entries; rendered in the order supplied.
 * @param currentRoute The current route label; compared against [NavigationBarItemContent]'s
 *   destination label to determine the active cell.
 * @param onSelect Invoked when the user taps a cell.
 * @param modifier Optional layout modifier.
 * @param itemModifier Optional per-cell modifier, e.g. for attaching `Modifier.testTag(...)` to
 *   individual cells from a calling wrapper.
 */
@Composable
fun KineticBottomNav(
  items: List<NavigationBarItemContent>,
  currentRoute: String,
  onSelect: (NavigationBarItemContent) -> Unit,
  modifier: Modifier = Modifier,
  itemModifier: (NavigationBarItemContent) -> Modifier = { Modifier },
) {
  val palette = LocalKineticPalette.current
  val typography = LocalKineticTypography.current
  Row(
    modifier =
      modifier
        .fillMaxWidth()
        // Background before the inset padding, so the panel colour paints under the gesture bar.
        .background(palette.panel)
        .windowInsetsPadding(WindowInsets.navigationBars)
        .height(BAR_HEIGHT)
        .drawBehind {
          drawRect(
            color = palette.line,
            topLeft = Offset(0f, 0f),
            size = Size(size.width, BAR_BORDER.toPx()),
          )
        }
        .padding(
          start = BAR_HORIZONTAL_PADDING,
          end = BAR_HORIZONTAL_PADDING,
          bottom = BAR_BOTTOM_PADDING,
        ),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceEvenly,
  ) {
    items.forEach { item ->
      val active = isActive(item, currentRoute)
      val style = kineticNavCellStyle(palette, active)
      val pill = remember { Animatable(style.pill) }
      // Keyed on the colour rather than on `active`, so a light↔dark palette flip repaints too.
      LaunchedEffect(style.pill) {
        // First composition (and any already-settled state) costs nothing: no spec allocation, no
        // reduce-motion settings read, and the active tab is painted final rather than fading in.
        if (pill.value != style.pill) {
          pill.animateTo(style.pill, KineticMotion.Routine.screenTransition())
        }
      }
      Column(
        modifier =
          Modifier.weight(1f)
            .fillMaxHeight()
            .then(itemModifier(item))
            .semantics { selected = active }
            // Tapping the cell for the screen you are already on is a no-op (avoids a pointless
            // re-navigation + transition); the node stays clickable so its test tag is tappable.
            .clickable(enabled = true, onClick = { if (!active) onSelect(item) }),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        Box(
          modifier =
            Modifier.drawBehind {
                drawRoundRect(color = pill.value, cornerRadius = CornerRadius(PILL_RADIUS.toPx()))
              }
              .padding(
                vertical = PILL_VERTICAL_PADDING,
                horizontal = PILL_HORIZONTAL_PADDING,
              )
        ) {
          NavCellIcon(item, style.content)
        }
        Spacer(modifier = Modifier.height(ICON_LABEL_SPACER))
        Text(
          // Kept uppercase (the mockup shows Title Case): the casing change is out of this
          // issue's scope. labelSm's 0.1em tracking is kept, uppercase needs it.
          text = stringResource(item.destination.displayNameRes()).uppercase(),
          style =
            typography.labelSm.copy(
              color = style.content,
              fontSize = LABEL_SIZE,
              fontWeight = style.labelWeight,
            ),
        )
      }
    }
  }
}
