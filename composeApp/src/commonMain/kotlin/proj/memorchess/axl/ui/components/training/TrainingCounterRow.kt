package proj.memorchess.axl.ui.components.training

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import memorchess.composeapp.generated.resources.Res
import memorchess.composeapp.generated.resources.training_fail
import memorchess.composeapp.generated.resources.training_left
import memorchess.composeapp.generated.resources.training_success
import org.jetbrains.compose.resources.stringResource

/**
 * Success, fail and left counters side by side, sharing the width evenly.
 *
 * @param successCount Successful attempts in the current session.
 * @param failCount Failed attempts in the current session.
 * @param leftCount Training entries still pending today.
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
      label = stringResource(Res.string.training_success),
      value = successCount,
      tone = KineticCounterTone.Success,
      modifier = Modifier.weight(1f),
      animateOnChange = true,
    )
    KineticCounterBlock(
      label = stringResource(Res.string.training_fail),
      value = failCount,
      tone = KineticCounterTone.Fail,
      modifier = Modifier.weight(1f),
    )
    KineticCounterBlock(
      label = stringResource(Res.string.training_left),
      value = leftCount,
      tone = KineticCounterTone.Neutral,
      modifier = Modifier.weight(1f),
    )
  }
}
