package proj.memorchess.axl.ui.components.training

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import proj.memorchess.axl.ui.theme.KineticMotion
import proj.memorchess.axl.ui.theme.LocalKineticPalette
import proj.memorchess.axl.ui.theme.LocalKineticTypography

/**
 * Visual tone of a [KineticCounterBlock].
 *
 * The tone controls the color of the 3.dp left accent stripe. The numeric value itself always uses
 * the [ink] color so the block reads as a neutral stat card rather than a colored badge.
 */
enum class KineticCounterTone {
  /** Progress left border — success / correct count. */
  Success,
  /** Destructive left border — failure / incorrect count. */
  Fail,
  /** Ink3 left border — generic stats such as "Left". */
  Neutral,
}

/**
 * Small numeric stat card used on the Training page.
 *
 * Follows artboards `1b`/`1h`: a 20.dp-radius panel card with a 1.5.dp `line` border and a 3.dp
 * colored stripe on the left whose color is selected by [tone]. The card is clipped to its shape
 * before the stripe is drawn, so the stripe's left end rounds with the card, and the stripe is
 * drawn over the border so all 3.dp of it stay visible. Inside, a small uppercase [label] sits
 * above a big Baloo 2 [value].
 *
 * The component never sets its own width; the caller is expected to provide it through [modifier]
 * (typically `Modifier.weight(1f)` inside a 3-cell Row). When called without any width modifier the
 * block falls back to [Modifier.wrapContentWidth] so an unconstrained call still renders.
 *
 * The [value] is rendered via [Int.toString] without thousand separators, so it gracefully handles
 * the full Int range including [Int.MAX_VALUE] and negative values (with a leading minus sign).
 *
 * @param animateOnChange When `true`, a value change rolls the digits up (the outgoing value
 *   slides/fades out upward, the incoming one slides/fades in from below), gated behind
 *   [KineticMotion.shouldAnimateBoardFeedback] so reduced motion swaps the value with no animation.
 */
@Composable
fun KineticCounterBlock(
  label: String,
  value: Int,
  tone: KineticCounterTone,
  modifier: Modifier = Modifier,
  animateOnChange: Boolean = false,
) {
  val palette = LocalKineticPalette.current
  val typography = LocalKineticTypography.current
  val stripeColor =
    when (tone) {
      KineticCounterTone.Success -> palette.progress
      KineticCounterTone.Fail -> palette.destructive
      KineticCounterTone.Neutral -> palette.ink3
    }

  val shape = MaterialTheme.shapes.medium

  Column(
    modifier =
      modifier
        .clip(shape)
        .background(palette.panel, shape)
        // Drawn after the content — and so after the border, which is chained below and paints on
        // top of what it wraps — or the 1.5.dp stroke would cover half the 3.dp stripe.
        .drawWithContent {
          drawContent()
          val stripePx = 3.dp.toPx()
          drawRect(
            color = stripeColor,
            topLeft = Offset(0f, 0f),
            size = Size(stripePx, size.height),
          )
        }
        .border(1.5.dp, palette.line, shape)
        .padding(14.dp),
    verticalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    Text(text = label.uppercase(), style = typography.labelSm.copy(color = palette.ink3))
    if (animateOnChange && KineticMotion.shouldAnimateBoardFeedback()) {
      AnimatedContent(
        targetState = value,
        transitionSpec = {
          (slideInVertically(KineticMotion.Celebratory.correctAnswer()) { height -> height } +
              fadeIn(KineticMotion.Celebratory.correctAnswer()))
            .togetherWith(
              slideOutVertically(KineticMotion.Celebratory.correctAnswer()) { height -> -height } +
                fadeOut(KineticMotion.Celebratory.correctAnswer())
            )
        },
        label = "counter value",
      ) { v ->
        Text(text = v.toString(), style = typography.displayLg.copy(color = palette.ink))
      }
    } else {
      Text(text = value.toString(), style = typography.displayLg.copy(color = palette.ink))
    }
  }
}
