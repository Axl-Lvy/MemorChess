package proj.memorchess.axl.ui.components.board

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import compose.icons.FeatherIcons
import compose.icons.feathericons.AlertCircle
import compose.icons.feathericons.ArrowRightCircle
import compose.icons.feathericons.CheckCircle
import compose.icons.feathericons.HelpCircle
import proj.memorchess.axl.core.graph.nodes.Node
import proj.memorchess.axl.ui.theme.goodTint

/**
 * State indicator
 *
 * This shows an indication of the node state.
 *
 * @param state The state of the node.
 */
@Composable
fun StateIndicator(modifier: Modifier = Modifier, state: Node.NodeState) {
  val unknownColor = MaterialTheme.colorScheme.outline
  val (color, icon, label) =
    when (state) {
      Node.NodeState.FIRST -> Triple(goodTint, FeatherIcons.ArrowRightCircle, "Start")
      Node.NodeState.SAVED_GOOD -> Triple(goodTint, FeatherIcons.CheckCircle, "Saved")
      Node.NodeState.SAVED_BAD -> Triple(goodTint, FeatherIcons.CheckCircle, "Partially saved")
      Node.NodeState.SAVED_GOOD_BUT_UNKNOWN_MOVE ->
        Triple(unknownColor, FeatherIcons.HelpCircle, "Saved (Unknown Move)")
      Node.NodeState.SAVED_BAD_BUT_UNKNOWN_MOVE ->
        Triple(unknownColor, FeatherIcons.HelpCircle, "Partially saved (Unknown Move)")
      Node.NodeState.UNKNOWN -> Triple(unknownColor, FeatherIcons.HelpCircle, "Unknown")
      Node.NodeState.BAD_STATE ->
        Triple(MaterialTheme.colorScheme.error, FeatherIcons.AlertCircle, "Bad State")
    }
  Box(
    modifier =
      modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(8.dp))
        .background(color = color.copy(alpha = 0.15f))
        .padding(vertical = 8.dp),
    contentAlignment = Alignment.Center,
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Icon(icon, contentDescription = label, tint = color)
      Text(
        text = label,
        color = color,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(start = 8.dp),
      )
    }
  }
}
