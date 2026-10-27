package proj.memorchess.axl.ui.components.buttons

import androidx.compose.runtime.Composable

/**
 * A child item of a [wide scrollable row][WideScrollableRow].
 *
 * @property drawContent Draws the child, given whether it is selected.
 */
data class WideScrollBarChild(
  val testTag: String,
  val onClick: () -> Unit,
  val drawContent: @Composable (Boolean) -> Unit,
)
