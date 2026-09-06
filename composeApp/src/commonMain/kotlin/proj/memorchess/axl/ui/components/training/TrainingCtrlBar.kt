package proj.memorchess.axl.ui.components.training

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import memorchess.composeapp.generated.resources.Res
import memorchess.composeapp.generated.resources.training_black_to_move
import memorchess.composeapp.generated.resources.training_hint
import memorchess.composeapp.generated.resources.training_reveal
import memorchess.composeapp.generated.resources.training_skip
import memorchess.composeapp.generated.resources.training_white_to_move
import org.jetbrains.compose.resources.stringResource
import proj.memorchess.axl.core.engine.Player
import proj.memorchess.axl.ui.components.buttons.KineticButton
import proj.memorchess.axl.ui.components.buttons.KineticButtonLabel
import proj.memorchess.axl.ui.components.buttons.KineticButtonStyle
import proj.memorchess.axl.ui.theme.KineticMotion
import proj.memorchess.axl.ui.theme.LocalKineticPalette
import proj.memorchess.axl.ui.theme.LocalKineticTypography

/** Vertical rise of the bar while a wrong answer is being shown. */
private val FEEDBACK_RAISE = 12.dp

/**
 * Bottom control bar for the Kinetic Training page.
 *
 * Follows artboards `1b`/`1h`. Layout, left to right:
 * - **SKIP** — default [KineticButton]; wired to [onSkip]. Like the other two it carries the chunky
 *   pressable edge every filled [KineticButton] now has.
 * - **Turn pill** — a small `panel2`-backed Box with a 12.dp radius and a 1.5.dp `line` border
 *   showing `"BLACK TO MOVE"` or `"WHITE TO MOVE"` in 10sp uppercase text, derived from
 *   [playerTurn].
 * - **HINT** and **REVEAL** — default + primary [KineticButton]s wired to [onHint] / [onReveal].
 *   These are stubs for v1; the actual hint/reveal logic is out of scope of this visual port.
 *
 * The whole bar sits on a 20.dp-radius `panel` background with a 1.5.dp `line` border. Padding is
 * 10.dp vertical / 12.dp horizontal so the bar feels tighter than the rest of the page. While
 * [feedbackActive] is `true` the whole bar rises by [FEEDBACK_RAISE].
 *
 * @param playerTurn The side whose turn it is; controls the turn pill text.
 * @param onSkip Invoked when the user taps SKIP.
 * @param onHint Invoked when the user taps HINT (stub in v1).
 * @param onReveal Invoked when the user taps REVEAL (stub in v1).
 * @param feedbackActive Whether a wrong answer is currently being shown; raises the whole bar.
 * @param modifier Modifier applied to the outer container.
 */
@Composable
fun TrainingCtrlBar(
  playerTurn: Player,
  onSkip: () -> Unit,
  onHint: () -> Unit,
  onReveal: () -> Unit,
  feedbackActive: Boolean = false,
  modifier: Modifier = Modifier,
) {
  val palette = LocalKineticPalette.current
  val typography = LocalKineticTypography.current
  val turnLabel =
    if (playerTurn == Player.BLACK) stringResource(Res.string.training_black_to_move)
    else stringResource(Res.string.training_white_to_move)

  val offsetY = remember { Animatable(0f) }
  val raisePx = with(LocalDensity.current) { -FEEDBACK_RAISE.toPx() }
  LaunchedEffect(feedbackActive) {
    val target = if (feedbackActive) raisePx else 0f
    if (KineticMotion.shouldAnimateBoardFeedback()) {
      offsetY.animateTo(target, KineticMotion.Routine.wrongAnswer())
    } else {
      offsetY.snapTo(target)
    }
  }

  Row(
    modifier =
      modifier
        .offset { IntOffset(0, offsetY.value.roundToInt()) }
        .background(palette.panel, MaterialTheme.shapes.medium)
        .border(1.5.dp, palette.line, MaterialTheme.shapes.medium)
        .padding(horizontal = 12.dp, vertical = 10.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    KineticButton(onClick = onSkip, style = KineticButtonStyle.Default) {
      KineticButtonLabel(stringResource(Res.string.training_skip))
    }
    Box(
      modifier =
        Modifier.weight(1f)
          .background(palette.panel2, MaterialTheme.shapes.extraSmall)
          .border(1.5.dp, palette.line, MaterialTheme.shapes.extraSmall)
          .padding(horizontal = 12.dp, vertical = 8.dp),
      contentAlignment = Alignment.Center,
    ) {
      Text(
        text = turnLabel,
        style = typography.labelSm.copy(fontSize = 10.sp, color = palette.ink2),
      )
    }
    KineticButton(onClick = onHint, style = KineticButtonStyle.Default) {
      KineticButtonLabel(stringResource(Res.string.training_hint))
    }
    KineticButton(onClick = onReveal, style = KineticButtonStyle.Primary) {
      KineticButtonLabel(stringResource(Res.string.training_reveal))
    }
  }
}
