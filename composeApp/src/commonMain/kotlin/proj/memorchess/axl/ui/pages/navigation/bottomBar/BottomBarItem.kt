package proj.memorchess.axl.ui.pages.navigation.bottomBar

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import memorchess.composeapp.generated.resources.Res
import memorchess.composeapp.generated.resources.icon_main
import org.jetbrains.compose.resources.painterResource
import proj.memorchess.axl.ui.pages.navigation.Destination

private const val BUTTON_SUFFIX = " button"

enum class BottomBarItem(
  val destination: Destination,
  val index: Int,
  val icon: @Composable () -> Unit,
) {
  Explore(
    destination = Destination.EXPLORE,
    index = 0,
    icon = {
      Icon(Icons.Rounded.Search, contentDescription = Destination.EXPLORE.name + BUTTON_SUFFIX)
    },
  ),
  Training(
    destination = Destination.TRAINING,
    index = 1,
    icon = {
      Icon(
        painterResource(Res.drawable.icon_main),
        contentDescription = Destination.TRAINING.name + BUTTON_SUFFIX,
        modifier = Modifier.size(32.dp),
      )
    },
  ),
  Settings(
    destination = Destination.SETTINGS,
    index = 2,
    icon = {
      Icon(Icons.Rounded.Settings, contentDescription = Destination.SETTINGS.name + BUTTON_SUFFIX)
    },
  ),
}
