package proj.ankichess.axl.ui.pages.navigation.bottomBar

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import proj.ankichess.axl.ui.pages.navigation.Destination

enum class BottomBarItem(
  val label: String,
  val icon: ImageVector,
  val destination: Destination,
  val index: Int,
) {
  Explore(
    label = Destination.EXPLORE.name,
    icon = Icons.Rounded.Search,
    destination = Destination.EXPLORE,
    index = 0,
  ),
  Settings(
    label = Destination.SETTINGS.name,
    icon = Icons.Rounded.Settings,
    destination = Destination.SETTINGS,
    index = 1,
  ),
}
