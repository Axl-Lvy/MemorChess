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
import androidx.compose.foundation.layout.height
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

/**
 * Scale a button settles to while held. Just short of 1, so the press registers without jumping.
 */
private const val PRESSED_SCALE = 0.972f

/** Alpha applied to a disabled button. */
private const val DISABLED_ALPHA = 0.5f

/** Stroke width of the button outline, whoever draws it. */
private val BORDER_WIDTH = 1.5.dp

/** Visual variant of a [KineticButton]. */
enum class KineticButtonStyle {
  /** Default panel-2 background, line-bright border — the most common button. */
  Default,
  /** Action (violet) fill — primary CTAs (Save, Reveal). */
  Primary,
  /** Destructive (pink) filled — dangerous actions (Erase all data). */
  Danger,
  /** Destructive (pink) outline only — softer dangerous actions (Reset settings, Disconnect). */
  DangerOutline,
  /** Transparent — used inline (toolbar overflow, secondary links). */
  Ghost,
  /**
   * Lime fill reserved for the Library hero card's CTA (and any future "picked for you" gamified
   * accent). Not part of the Default/Primary/Danger/DangerOutline/Ghost ladder used elsewhere:
   * light's `progress` role collapses onto `action` (see [KineticPalette]'s own KDoc), which reads
   * as invisible against a violet fill, so this style needs a literal lime distinct from every
   * existing role. See #282.
   */
  Accent,
}

private data class ButtonColors(
  val background: Color,
  val border: Color,
  val content: Color,
  val hoverBackground: Color,
  val hoverBorder: Color,
  val hoverContent: Color,
  /**
   * Whether this style carries the chunky hard bottom edge. Only the filled styles do: the edge is
   * an affordance of a solid button face, and giving a transparent style a 4.dp edge plus the
   * elevation's own outline would draw a phantom box around an inline link.
   */
  val elevated: Boolean,
)

/**
 * Literal lime fill for [KineticButtonStyle.Accent] and the Library re-skin's gamified badges
 * ("PICKED FOR YOU", "IN TRAINING"). In light theme `palette.progress` is violet (it collapses onto
 * `action`, documented on [KineticPalette]), which would vanish against the hero card's violet fill
 * — so light needs a genuinely distinct hue here. Dark's `progress` already is this lime, so dark
 * reuses it directly rather than duplicating the literal.
 */
internal fun kineticAccentLimeColor(palette: KineticPalette): Color =
  if (palette.isLight) Color(0xFFA8F03A) else palette.progress

/**
 * Fixed dark-green text color that reads on [kineticAccentLimeColor] in both themes. Not
 * `palette.onProgress`: that is white in light, tuned for `progress`'s violet fill there, not this
 * lime one.
 */
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
        // Chunky edge; kineticPressableEdgeColor always returns palette.lineBright rather than a
        // per-fill darker lime — a pre-existing, documented gap (see that function's own KDoc),
        // out of scope for #282.
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

/** Whether the button reads as "active" — hovered or pressed, and not disabled either way. */
private fun isButtonActive(enabled: Boolean, hovered: Boolean, pressed: Boolean): Boolean =
  enabled && (hovered || pressed)

/** A button's footprint: height and corner radius, keyed only by [large]. */
private data class ButtonMetrics(val height: Dp, val shape: Shape)

@Composable
private fun buttonMetrics(large: Boolean): ButtonMetrics =
  if (large) ButtonMetrics(44.dp, MaterialTheme.shapes.small)
  else ButtonMetrics(36.dp, MaterialTheme.shapes.extraSmall)

/**
 * Animates [this] to the pressed scale (or back to 1f). The spec is built here, at press time,
 * rather than where the [Animatable] is created, so mounting a button costs no settings read.
 */
private suspend fun Animatable<Float, AnimationVector1D>.settleForPress(pressed: Boolean) {
  // Also runs on first composition with pressed == false. Returning before building the spec
  // keeps the Koin-backed reduce-motion read a press-time cost, not a mount-time one.
  if (!pressed && value == 1f) return
  val spec = KineticMotion.Routine.buttonPress<Float>()
  animateTo(if (pressed) PRESSED_SCALE else 1f, spec)
}

/**
 * Everything [Modifier.kineticButtonShell] needs, bundled into one parameter so the function itself
 * stays under Sonar's 7-parameter ceiling (kotlin:S107) rather than taking each field separately.
 */
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

/**
 * Builds the pressable button's shell modifier: sizing, the click target, the press-scale layer,
 * the chunky pressable edge (filled styles only) or a plain stroke (transparent styles), the fill,
 * and finally the clipped indication layer.
 *
 * Order is load-bearing and kept exactly as authored: pointer input first, since the elevation's
 * press translate is a layout offset and a `clickable` chained after it would slide the touch
 * target out from under a finger held near the top edge; the indication is applied further down
 * instead, where it can be clipped to the shape. The elevated styles hand their outline to the
 * elevation, which draws it inside the press offset — a `border` chained after the elevation would
 * be painted over by its own stroke, so the plain `border` only runs for non-elevated styles. The
 * background is placed after the elevation, or the hard edge gets painted over. The final `clip`
 * covers only the state layer: the elevation's hard edge is drawn outside the node's bounds and a
 * clip chained above it would cut the edge off.
 *
 * [ButtonShellState.pressScale] is read as `.value` inside the `graphicsLayer` block (the draw
 * phase), never during composition, so animating it costs a redraw, not a recomposition.
 */
@Composable
private fun Modifier.kineticButtonShell(state: ButtonShellState): Modifier =
  this.height(state.metrics.height)
    .defaultMinSize(minWidth = state.metrics.height)
    .then(if (state.iconOnly) Modifier.width(state.metrics.height) else Modifier)
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
    .then(
      if (state.elevated) {
        Modifier.kineticPressableElevation(state.pressed, state.metrics.shape, state.outline)
      } else Modifier
    )
    .background(color = state.background, shape = state.metrics.shape)
    .then(if (state.elevated) Modifier else Modifier.border(state.outline, state.metrics.shape))
    .clip(state.metrics.shape)
    .indication(state.interactionSource, state.indication)
    .then(if (state.iconOnly) Modifier else Modifier.padding(horizontal = 14.dp))

/**
 * Kinetic button. Mirrors `.btn`, `.btn.primary`, `.btn.danger`, `.btn.danger.outline`,
 * `.btn.icon-only`, and `.btn.lg` from `design-proposals/kinetic-base.css`.
 *
 * Buttons round to 12.dp at the default 36.dp height and to 16.dp when [large] (44.dp, the CTAs
 * used in Settings rows), carry a 1.5.dp border and a Baloo 2 600 12sp label. Set [iconOnly] for a
 * square (height × height) toolbar button with no horizontal padding and the same radius.
 *
 * The filled styles (Default, Primary, Danger, Accent) carry the chunky Kinetic pressable hard
 * bottom edge: 4.dp at rest, collapsing to a 1.dp sliver on press while the button translates 3.dp
 * down. The two transparent styles (DangerOutline, Ghost) do not — that edge is an affordance of a
 * solid button face. Every style animates a small press scale through
 * [KineticMotion.Routine.buttonPress], whose spec is built on the first real press rather than on
 * mount, so composing a button costs no settings read.
 *
 * The button uses [LocalIndication] for the ripple/highlight indication, so it picks up whatever
 * the surrounding Material theme provides on each target. The indication is applied behind a [clip]
 * to the button's shape rather than through `clickable`, so the state layer follows the rounded
 * face instead of the node's square bounds — the elevation's hard edge is drawn outside those
 * bounds and must stay unclipped.
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

/** Convenience helper for the common case of a label-only Kinetic button. */
@Composable
fun KineticButtonLabel(text: String) {
  Text(text = text.uppercase())
}
