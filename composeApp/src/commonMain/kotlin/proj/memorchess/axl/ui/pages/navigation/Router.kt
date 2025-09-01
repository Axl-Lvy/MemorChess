package proj.memorchess.axl.ui.pages.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import org.koin.compose.koinInject
import proj.memorchess.axl.core.data.PositionIdentifier
import proj.memorchess.axl.ui.pages.Explore
import proj.memorchess.axl.ui.pages.Settings
import proj.memorchess.axl.ui.pages.Training

@Composable
fun Router(modifier: Modifier = Modifier, navController: Navigator = koinInject()) {
  NavHost(
    navController = (navController as DelegateNavigator).navController,
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
    Explore(
      position?.let { p ->
        PositionIdentifier.validateAndCreateOrNull(
          p.toPosition().createIdentifier().fenRepresentation
        )
      }
    )
  }
}
