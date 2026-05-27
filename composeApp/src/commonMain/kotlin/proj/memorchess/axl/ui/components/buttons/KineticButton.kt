package proj.memorchess.axl.ui.components.buttons

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import proj.memorchess.axl.ui.theme.KineticPalette
import proj.memorchess.axl.ui.theme.LocalKineticPalette
import proj.memorchess.axl.ui.theme.LocalKineticTypography

/** Visual variant of a [KineticButton]. */
enum class KineticButtonStyle {
  /** Default panel-2 background, line-bright border — the most common button. */
  Default,
  /** Accent orange fill — primary CTAs (Save, Reveal). */
  Primary,
  /** Red filled — destructive actions (Erase all data). */
  Danger,
  /** Red outline only — softer destructive actions (Reset settings, Disconnect). */
  DangerOutline,
  /** Transparent — used inline (toolbar overflow, secondary links). */
  Ghost,
}

private data class ButtonColors(
  val background: Color,
  val border: Color,
  val content: Color,
  val hoverBackground: Color,
  val hoverBorder: Color,
  val hoverContent: Color,
)

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
      )
    KineticButtonStyle.Primary ->
      ButtonColors(
        background = palette.accent,
        border = palette.accent,
        content = palette.onAccent,
        hoverBackground = palette.accentGlow,
        hoverBorder = palette.accentGlow,
        hoverContent = palette.onAccent,
      )
    KineticButtonStyle.Danger ->
      ButtonColors(
        background = palette.red,
        border = palette.red,
        content = palette.onAccent,
        hoverBackground = palette.red,
        hoverBorder = palette.red,
        hoverContent = palette.onAccent,
      )
    KineticButtonStyle.DangerOutline ->
      ButtonColors(
        background = Color.Transparent,
        border = palette.redDim,
        content = palette.red,
        hoverBackground = palette.red,
        hoverBorder = palette.red,
        hoverContent = palette.onAccent,
      )
    KineticButtonStyle.Ghost ->
      ButtonColors(
        background = Color.Transparent,
        border = Color.Transparent,
        content = palette.ink3,
        hoverBackground = Color.Transparent,
        hoverBorder = Color.Transparent,
        hoverContent = palette.ink,
      )
  }

/**
 * Kinetic button. Mirrors `.btn`, `.btn.primary`, `.btn.danger`, `.btn.danger.outline`,
 * `.btn.icon-only`, and `.btn.lg` from `design-proposals/kinetic-base.css`.
 *
 * Buttons are intentionally square (no rounded corners), with a 1.dp border and Bricolage 600 12sp
 * label. Default height is 36.dp; set [large] to true for the 44.dp CTAs used in Settings rows. Set
 * [iconOnly] for a square (height × height) toolbar button with no horizontal padding.
 *
 * The button uses [LocalIndication] for the ripple/highlight indication, so it picks up whatever
 * the surrounding Material theme provides on each target.
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
  val active = enabled && (hovered || pressed)
  val bg = if (active) colors.hoverBackground else colors.background
  val borderColor = if (active) colors.hoverBorder else colors.border
  val fg = if (active) colors.hoverContent else colors.content
  val indication = LocalIndication.current
  val height = if (large) 44.dp else 36.dp

  Box(
    modifier =
      modifier
        .height(height)
        .defaultMinSize(minWidth = height)
        .alpha(if (enabled) 1f else 0.5f)
        .then(if (iconOnly) Modifier.width(height) else Modifier)
        .clickable(
          interactionSource = interactionSource,
          indication = indication,
          enabled = enabled,
          role = Role.Button,
          onClick = onClick,
        )
        .background(color = bg)
        .border(BorderStroke(1.dp, borderColor))
        .then(if (iconOnly) Modifier else Modifier.padding(horizontal = 14.dp)),
    contentAlignment = Alignment.Center,
  ) {
    CompositionLocalProvider(
      LocalContentColor provides fg,
      LocalTextStyle provides typography.display.copy(fontSize = 12.sp, color = fg),
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
