package proj.memorchess.axl.ui.components.buttons

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Indication
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import proj.memorchess.axl.ui.theme.KineticMotion
import proj.memorchess.axl.ui.theme.KineticPalette
import proj.memorchess.axl.ui.theme.LocalKineticPalette
import proj.memorchess.axl.ui.theme.LocalKineticTypography
import proj.memorchess.axl.ui.theme.kineticPressableElevation

/** Scale a held button settles to, just short of 1 so the press registers without a jump. */
private const val PRESSED_SCALE = 0.972f

/** Alpha applied to a disabled button. */
private const val DISABLED_ALPHA = 0.5f

/** Stroke width of the button outline. */
private val BORDER_WIDTH = 1.5.dp

/** Visual variant of a [KineticButton]. */
enum class KineticButtonStyle {
  /** Panel fill with a bright border. */
  Default,
  /** Action fill, for primary actions. */
  Primary,
  /** Destructive fill, for dangerous actions. */
  Danger,
  /** Destructive outline, for softer dangerous actions. */
  DangerOutline,
  /** Transparent, for inline actions. */
  Ghost,
  /** Lime fill for gamified accents, distinct from every palette role. */
  Accent,
}

private data class ButtonColors(
  val background: Color,
  val border: Color,
  val content: Color,
  val hoverBackground: Color,
  val hoverBorder: Color,
  val hoverContent: Color,
  /** Whether the style has the hard bottom edge; on a transparent style it draws a phantom box. */
  val elevated: Boolean,
)

/**
 * Lime fill for [KineticButtonStyle.Accent] and gamified badges. Light needs a literal, since its
 * `progress` collapses onto the violet `action`.
 */
internal fun kineticAccentLimeColor(palette: KineticPalette): Color =
  if (palette.isLight) Color(0xFFA8F03A) else palette.progress

/** Dark-green text color that reads on [kineticAccentLimeColor] in both themes. */
internal val KineticOnAccentLime: Color = Color(0xFF24350A)

private fun resolveColors(style: KineticButtonStyle, palette: KineticPalette): ButtonColors =
  when (style) {
    KineticButtonStyle.Default ->
      ButtonColors(
        background = palette.panel2,
        border = palette.lineBright,
        content = palette.ink2,
        hoverBackground = palette.panel2,
        hoverBorder = palette.ink3,
        hoverContent = palette.ink,
        elevated = true,
      )
    KineticButtonStyle.Primary ->
      ButtonColors(
        background = palette.action,
        border = palette.action,
        content = palette.onAction,
        hoverBackground = palette.actionGlow,
        hoverBorder = palette.actionGlow,
        hoverContent = palette.onAction,
        elevated = true,
      )
    KineticButtonStyle.Danger ->
      ButtonColors(
        background = palette.destructive,
        border = palette.destructive,
        content = palette.onDestructive,
        hoverBackground = palette.destructive,
        hoverBorder = palette.destructive,
        hoverContent = palette.onDestructive,
        elevated = true,
      )
    KineticButtonStyle.DangerOutline ->
      ButtonColors(
        background = Color.Transparent,
        border = palette.destructiveDim,
        content = palette.destructive,
        hoverBackground = palette.destructive,
        hoverBorder = palette.destructive,
        hoverContent = palette.onDestructive,
        elevated = false,
      )
    KineticButtonStyle.Ghost ->
      ButtonColors(
        background = Color.Transparent,
        border = Color.Transparent,
        content = palette.ink3,
        hoverBackground = Color.Transparent,
        hoverBorder = Color.Transparent,
        hoverContent = palette.ink,
        elevated = false,
      )
    KineticButtonStyle.Accent -> {
      val fill = kineticAccentLimeColor(palette)
      ButtonColors(
        background = fill,
        border = fill,
        content = KineticOnAccentLime,
        hoverBackground = fill,
        hoverBorder = fill,
        hoverContent = KineticOnAccentLime,
        elevated = true,
      )
    }
  }

/** The background/border/content triple for one hover/press state of [ButtonColors]. */
private data class ButtonFace(val background: Color, val border: Color, val content: Color)

/** Resolves [this] to its hovered or resting face, depending on [active]. */
private fun ButtonColors.faceFor(active: Boolean): ButtonFace =
  if (active) ButtonFace(hoverBackground, hoverBorder, hoverContent)
  else ButtonFace(background, border, content)

/** Whether the button is enabled and hovered or pressed. */
private fun isButtonActive(enabled: Boolean, hovered: Boolean, pressed: Boolean): Boolean =
  enabled && (hovered || pressed)

/** A button's minimum height and shape. */
private data class ButtonMetrics(val height: Dp, val shape: Shape)

@Composable
private fun buttonMetrics(large: Boolean): ButtonMetrics =
  if (large) ButtonMetrics(44.dp, MaterialTheme.shapes.small)
  else ButtonMetrics(36.dp, MaterialTheme.shapes.extraSmall)

/** Animates [this] to the pressed scale, or back to 1f. */
private suspend fun Animatable<Float, AnimationVector1D>.settleForPress(pressed: Boolean) {
  // Returning before building the spec keeps the Koin-backed reduce-motion read off mount.
  if (!pressed && value == 1f) return
  val spec = KineticMotion.Routine.buttonPress<Float>()
  animateTo(if (pressed) PRESSED_SCALE else 1f, spec)
}

/** Inputs of [Modifier.kineticButtonShell], bundled to stay under Sonar's kotlin:S107 limit. */
private class ButtonShellState(
  val iconOnly: Boolean,
  val enabled: Boolean,
  val metrics: ButtonMetrics,
  val interactionSource: MutableInteractionSource,
  val onClick: () -> Unit,
  val pressScale: Animatable<Float, AnimationVector1D>,
  val elevated: Boolean,
  val pressed: Boolean,
  val outline: BorderStroke,
  val background: Color,
  val indication: Indication,
)

/** Builds the button's sizing, click target, press scale, edge, fill and indication. */
@Composable
private fun Modifier.kineticButtonShell(state: ButtonShellState): Modifier =
  this.heightIn(min = state.metrics.height)
    .defaultMinSize(minWidth = state.metrics.height)
    .then(if (state.iconOnly) Modifier.width(state.metrics.height) else Modifier)
    // Before the elevation, whose press offset would otherwise slide the touch target away.
    .clickable(
      interactionSource = state.interactionSource,
      indication = null,
      enabled = state.enabled,
      role = Role.Button,
      onClick = state.onClick,
    )
    .graphicsLayer {
      alpha = if (state.enabled) 1f else DISABLED_ALPHA
      scaleX = state.pressScale.value
      scaleY = state.pressScale.value
    }
    // Elevated styles draw their outline inside the elevation, so they skip the plain border.
    .then(
      if (state.elevated) {
        Modifier.kineticPressableElevation(state.pressed, state.metrics.shape, state.outline)
      } else Modifier
    )
    // After the elevation, or the hard edge gets painted over.
    .background(color = state.background, shape = state.metrics.shape)
    .then(if (state.elevated) Modifier else Modifier.border(state.outline, state.metrics.shape))
    // Clips only the indication: the hard edge is drawn outside the bounds.
    .clip(state.metrics.shape)
    .indication(state.interactionSource, state.indication)
    .then(if (state.iconOnly) Modifier else Modifier.padding(horizontal = 14.dp))

/**
 * Kinetic button in the given [style]; [large] for a taller CTA, [iconOnly] for a square one. The
 * height is a floor: a wrapping label grows the button instead of being clipped.
 */
@Composable
fun KineticButton(
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  style: KineticButtonStyle = KineticButtonStyle.Default,
  enabled: Boolean = true,
  iconOnly: Boolean = false,
  large: Boolean = false,
  content: @Composable () -> Unit,
) {
  val palette = LocalKineticPalette.current
  val typography = LocalKineticTypography.current
  val colors = resolveColors(style, palette)
  val interactionSource = remember { MutableInteractionSource() }
  val hovered by interactionSource.collectIsHoveredAsState()
  val pressed by interactionSource.collectIsPressedAsState()
  val active = isButtonActive(enabled, hovered, pressed)
  val face = colors.faceFor(active)
  val indication = LocalIndication.current
  val metrics = buttonMetrics(large)
  val outline = BorderStroke(BORDER_WIDTH, face.border)
  val pressScale = remember { Animatable(1f) }

  LaunchedEffect(pressed) { pressScale.settleForPress(pressed) }

  Box(
    modifier =
      modifier.kineticButtonShell(
        ButtonShellState(
          iconOnly = iconOnly,
          enabled = enabled,
          metrics = metrics,
          interactionSource = interactionSource,
          onClick = onClick,
          pressScale = pressScale,
          elevated = colors.elevated,
          pressed = pressed,
          outline = outline,
          background = face.background,
          indication = indication,
        )
      ),
    contentAlignment = Alignment.Center,
  ) {
    CompositionLocalProvider(
      LocalContentColor provides face.content,
      LocalTextStyle provides typography.display.copy(fontSize = 12.sp, color = face.content),
    ) {
      Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        content()
      }
    }
  }
}

/** Uppercased label for a [KineticButton]. */
@Composable
fun KineticButtonLabel(text: String) {
  Text(text = text.uppercase())
}
