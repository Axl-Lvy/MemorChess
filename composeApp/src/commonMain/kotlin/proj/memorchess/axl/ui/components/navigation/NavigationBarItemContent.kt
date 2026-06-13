package proj.memorchess.axl.ui.components.navigation

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import memorchess.composeapp.generated.resources.Res
import memorchess.composeapp.generated.resources.icon_main
import memorchess.composeapp.generated.resources.nav_button_content_description
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import proj.memorchess.axl.ui.pages.navigation.Route

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
        contentDescription =
          stringResource(
            Res.string.nav_button_content_description,
            stringResource(Route.ExploreRoute.DEFAULT.displayNameRes()),
          ),
      )
    },
  ),
  Training(
    destination = Route.TrainingRoute,
    index = 1,
    icon = {
      Icon(
        painterResource(Res.drawable.icon_main),
        contentDescription =
          stringResource(
            Res.string.nav_button_content_description,
            stringResource(Route.TrainingRoute.displayNameRes()),
          ),
        modifier = Modifier.size(32.dp),
      )
    },
  ),
  Library(
    destination = Route.LibraryRoute,
    index = 2,
    icon = {
      Icon(
        Icons.Rounded.Star,
        contentDescription =
          stringResource(
            Res.string.nav_button_content_description,
            stringResource(Route.LibraryRoute.displayNameRes()),
          ),
      )
    },
  ),
  Settings(
    destination = Route.SettingsRoute,
    index = 3,
    icon = {
      Icon(
        Icons.Rounded.Settings,
        contentDescription =
          stringResource(
            Res.string.nav_button_content_description,
            stringResource(Route.SettingsRoute.displayNameRes()),
          ),
      )
    },
  ),
}
