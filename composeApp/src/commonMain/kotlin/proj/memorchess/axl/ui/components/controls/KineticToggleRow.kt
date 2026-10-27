package proj.memorchess.axl.ui.components.controls

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import proj.memorchess.axl.ui.theme.LocalKineticPalette
import proj.memorchess.axl.ui.theme.LocalKineticTypography

/**
 * A row with [label] on the left and a [KineticToggle] on the right.
 *
 * @param testTag Applied to the toggle.
 */
@Composable
fun KineticToggleRow(
  label: String,
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
  testTag: String,
  modifier: Modifier = Modifier,
) {
  val palette = LocalKineticPalette.current
  val typography = LocalKineticTypography.current
  Row(
    modifier = modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Text(text = label, style = typography.body.copy(color = palette.ink2))
    KineticToggle(
      checked = checked,
      onCheckedChange = onCheckedChange,
      modifier = Modifier.testTag(testTag),
    )
  }
}
