package proj.memorchess.axl.ui.components.buttons

import androidx.compose.runtime.Composable

/**
 * Represents a child item for use in a [wide scrollable row][WideScrollableRow] UI component.
 *
 * @property testTag A string used for testing purposes to identify this child.
 * @property onClick Callback invoked when the child is clicked.
 * @property drawContent Composable lambda to draw the content of the child. Receives a Boolean
 *   indicating if the child is selected.
 */
data class WideScrollBarChild(
  val testTag: String,
  val onClick: () -> Unit,
  val drawContent: @Composable (Boolean) -> Unit,
)
