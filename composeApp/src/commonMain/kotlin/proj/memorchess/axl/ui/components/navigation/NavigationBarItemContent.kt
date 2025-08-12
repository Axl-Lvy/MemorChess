package proj.memorchess.axl.ui.components.navigation

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
import proj.memorchess.axl.ui.pages.navigation.Route

private const val BUTTON_SUFFIX = " button"

enum class NavigationBarItemContent(
  val destination: Route,
  val index: Int,
  val icon: @Composable () -> Unit,
) {
  Explore(
    destination = Route.ExploreRoute.DEFAULT,
    index = 0,
    icon = {
      Icon(
        Icons.Rounded.Search,
        contentDescription = Route.ExploreRoute.DEFAULT.getLabel() + BUTTON_SUFFIX,
      )
    },
  ),
  Training(
    destination = Route.TrainingRoute,
    index = 1,
    icon = {
      Icon(
        painterResource(Res.drawable.icon_main),
        contentDescription = Route.TrainingRoute.getLabel() + BUTTON_SUFFIX,
        modifier = Modifier.size(32.dp),
      )
    },
  ),
  Settings(
    destination = Route.SettingsRoute,
    index = 2,
    icon = {
      Icon(
        Icons.Rounded.Settings,
        contentDescription = Route.SettingsRoute.getLabel() + BUTTON_SUFFIX,
      )
    },
  ),
}
