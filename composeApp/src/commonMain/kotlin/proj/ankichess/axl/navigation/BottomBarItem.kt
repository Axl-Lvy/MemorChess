package proj.ankichess.axl.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Search
import androidx.compose.ui.graphics.vector.ImageVector

enum class BottomBarItem(
  val label: String,
  val icon: ImageVector,
  val destination: Destination,
  val index: Int,
) {
  Training(
    label = "Training",
    icon = Icons.Rounded.Check,
    destination = Destination.TRAINING,
    index = 0,
  ),
  ManageSaves(
    label = "Manage Saves",
    icon = Icons.Rounded.Search,
    destination = Destination.EXPLORE,
    index = 1,
  ),
}
