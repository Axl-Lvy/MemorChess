package proj.memorchess.axl.ui.components.navigation

import androidx.compose.animation.Animatable
import androidx.compose.animation.core.Animatable as CoreAnimatable
import androidx.compose.animation.core.AnimationVector1D
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
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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

private val BAR_BORDER = 1.5.dp

private val ICON_SIZE = 23.dp

private val ICON_LABEL_SPACER = 4.dp

private val BAR_HEIGHT = 80.dp

private val BAR_HORIZONTAL_PADDING = 6.dp

private val BAR_BOTTOM_PADDING = 10.dp

private val PILL_RADIUS = 14.dp

private val PILL_VERTICAL_PADDING = 5.dp

private val PILL_HORIZONTAL_PADDING = 16.dp

private val LABEL_SIZE = 9.5.sp

/**
 * Whether [currentRoute] matches one of [item]'s route labels; back stack routes are lower case.
 */
internal fun isActive(item: NavigationBarItemContent, currentRoute: String): Boolean =
  item.ownedRouteLabels.any { it.equals(currentRoute, ignoreCase = true) }

/**
 * Resolved look of one navigation cell. An inactive [pill] is `actionDim` at alpha 0, not
 * [Color.Transparent], so the Oklab fade keeps its hue instead of passing through grey.
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

/** Renders [item]'s icon at [size] tinted with [tint], popping once when [active] turns true. */
@Composable
internal fun NavCellIcon(
  item: NavigationBarItemContent,
  tint: Color,
  size: Dp = ICON_SIZE,
  active: Boolean = false,
) {
  val scale = remember { CoreAnimatable(1f) }
  val wasActive = remember { mutableStateOf(active) }
  LaunchedEffect(active) { scale.pop(active, wasActive) }
  Box(
    modifier =
      Modifier.size(size).graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
      },
    contentAlignment = Alignment.Center,
  ) {
    CompositionLocalProvider(LocalContentColor provides tint) { item.icon() }
  }
}

/** Pops the icon scale to 1.12 and back, once per transition from inactive to active. */
private suspend fun CoreAnimatable<Float, AnimationVector1D>.pop(
  active: Boolean,
  wasActive: MutableState<Boolean>,
) {
  val justActivated = active && !wasActive.value
  wasActive.value = active
  // Reset first, so tapping away mid pop snaps back to rest instead of freezing.
  if (!active) {
    if (value != 1f) snapTo(1f)
    return
  }
  // A tab that mounts already active must not pop.
  if (!justActivated) return
  // Reduced motion means no pop at all, not a flattened one, so return before any animateTo.
  if (!KineticMotion.shouldPlayIconPop()) return
  val spec = KineticMotion.Routine.iconPop<Float>()
  animateTo(1.12f, spec)
  animateTo(1f, spec)
}

/** Mobile bottom navigation bar of icon and label cells, the active one on a filled pill. */
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
        // Settled states skip the animation, so the first composition paints the final pill.
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
            // Re-tapping the current screen is a no-op, yet the node stays clickable for tests.
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
          NavCellIcon(item, style.content, active = active)
        }
        Spacer(modifier = Modifier.height(ICON_LABEL_SPACER))
        Text(
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
