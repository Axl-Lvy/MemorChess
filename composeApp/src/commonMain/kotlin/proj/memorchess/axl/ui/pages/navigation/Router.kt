package proj.memorchess.axl.ui.pages.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import proj.memorchess.axl.core.data.PositionKey
import proj.memorchess.axl.ui.pages.Explore
import proj.memorchess.axl.ui.pages.Settings
import proj.memorchess.axl.ui.pages.Training

/**
 * Renders the navigation graph.
 *
 * The [navController] is owned by the caller (normally [proj.memorchess.axl.ui.App]) so that its
 * lifecycle stays composition scoped. Descendants that need to issue navigation actions read the
 * [Navigator] from [LocalNavigator] instead of going through this parameter.
 */
@Composable
fun Router(navController: NavHostController, modifier: Modifier = Modifier) {
  NavHost(
    navController = navController,
    startDestination = Route.TrainingRoute,
    modifier = modifier,
  ) {
    trainingRoute()
    settingsRoute()
    exploreRoute()
  }
}

private fun NavGraphBuilder.trainingRoute() {
  composable<Route.TrainingRoute> { Training() }
}

private fun NavGraphBuilder.settingsRoute() {
  composable<Route.SettingsRoute> { Settings() }
}

private fun NavGraphBuilder.exploreRoute() {
  composable<Route.ExploreRoute> {
    val position = it.toRoute<Route.ExploreRoute>().position
    Explore(position?.let { p -> PositionKey.validateAndCreateOrNull(p) })
  }
}
