package proj.memorchess.axl.ui.pages.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import proj.memorchess.axl.core.data.PositionIdentifier
import proj.memorchess.axl.ui.pages.Explore
import proj.memorchess.axl.ui.pages.Settings
import proj.memorchess.axl.ui.pages.Training

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

fun NavGraphBuilder.trainingRoute() {
  composable<Route.TrainingRoute> { Training() }
}

fun NavGraphBuilder.settingsRoute() {
  composable<Route.SettingsRoute> { Settings() }
}

fun NavGraphBuilder.exploreRoute() {
  composable<Route.ExploreRoute> {
    val position = it.toRoute<Route.ExploreRoute>().position
    Explore(position?.let { p -> PositionIdentifier(p) })
  }
}
