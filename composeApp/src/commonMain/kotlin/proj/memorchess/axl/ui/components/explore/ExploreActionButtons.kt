package proj.memorchess.axl.ui.components.explore

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import proj.memorchess.axl.ui.theme.LocalKineticPalette

/**
 * Legacy action row used by call sites that still pass [saveButton] / [deleteButton] slots
 * directly. Newer call sites should use [ExploreCtrlBar] instead. Kept so any code still wired
 * through this stays compiling.
 */
@Composable
fun ExploreActionButtons(
  modifier: Modifier = Modifier,
  saveButton: @Composable (Modifier) -> Unit,
  deleteButton: @Composable (Modifier) -> Unit,
) {
  val palette = LocalKineticPalette.current
  Row(
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalAlignment = Alignment.CenterVertically,
    modifier =
      modifier
        .fillMaxWidth()
        .background(palette.panel2)
        .border(width = 1.dp, color = palette.line)
        .padding(horizontal = 12.dp, vertical = 8.dp),
  ) {
    saveButton(Modifier.weight(1f))
    deleteButton(Modifier.weight(1f))
  }
}
