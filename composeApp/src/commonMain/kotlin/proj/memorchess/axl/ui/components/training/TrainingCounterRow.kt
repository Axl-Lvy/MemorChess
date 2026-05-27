package proj.memorchess.axl.ui.components.training

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Three-counter row used by the Kinetic Training page.
 *
 * Mirrors the `.counter-block` / `.counters-row` rule in `design-proposals/kinetic-base.css` —
 * SUCCESS, FAIL, LEFT side by side, each rendered through [KineticCounterBlock] with its semantic
 * [KineticCounterTone].
 *
 * The three [KineticCounterBlock] instances are placed inside a [Row] with `Arrangement.spacedBy`
 * and each receives `Modifier.weight(1f)` so the row distributes evenly across the available width.
 * All three counts are unconstrained [Int]s and gracefully render any value (including `0` and
 * [Int.MAX_VALUE]) via [Int.toString], per the numeric-edge-cases rule.
 *
 * @param successCount Number of successful attempts in the current session.
 * @param failCount Number of failed attempts in the current session.
 * @param leftCount Number of training entries still pending today.
 * @param modifier Modifier applied to the outer [Row].
 */
@Composable
fun TrainingCounterRow(
  successCount: Int,
  failCount: Int,
  leftCount: Int,
  modifier: Modifier = Modifier,
) {
  Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    KineticCounterBlock(
      label = "SUCCESS",
      value = successCount,
      tone = KineticCounterTone.Success,
      modifier = Modifier.weight(1f),
    )
    KineticCounterBlock(
      label = "FAIL",
      value = failCount,
      tone = KineticCounterTone.Fail,
      modifier = Modifier.weight(1f),
    )
    KineticCounterBlock(
      label = "LEFT",
      value = leftCount,
      tone = KineticCounterTone.Neutral,
      modifier = Modifier.weight(1f),
    )
  }
}
