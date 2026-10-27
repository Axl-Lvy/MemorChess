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

/** Color of a [KineticCounterBlock]'s left stripe; the value itself always uses `ink`. */
enum class KineticCounterTone {
  /** Progress stripe, for a correct count. */
  Success,
  /** Destructive stripe, for an incorrect count. */
  Fail,
  /** Ink3 stripe, for generic stats. */
  Neutral,
}

/**
 * Small stat card: an uppercase [label] over a large [value], with a left stripe colored by [tone].
 * The caller sets its width through [modifier].
 *
 * @param animateOnChange When `true`, a value change rolls the digits up unless motion is reduced.
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
        // Draws after its content, which includes the border chained below, so the stroke cannot
        // cover half the stripe.
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
