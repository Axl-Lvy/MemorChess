package proj.memorchess.axl.ui.pages.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavBackStackEntry
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
 * Ordinal of a destination along the navigation bar (Explore `0`, Training `1`, Settings `2`).
 *
 * Drives the direction of the screen transition: navigating toward a higher ordinal wipes forward,
 * toward a lower one wipes backward. Matched against the destination route string (which carries
 * the route's [kotlinx.serialization.SerialName]) so it tolerates the trailing `?position=…`
 * argument on the explore route and any package qualifier. Defaults to Training's ordinal for any
 * unrecognised destination.
 */
internal fun NavBackStackEntry.routeOrdinal(): Int {
  val route = destination.route.orEmpty().lowercase()
  return when {
    route.contains("explore") -> 0
    route.contains("training") -> 1
    route.contains("settings") -> 2
    else -> 1
  }
}

/**
 * Renders the navigation graph.
 *
 * The [navController] is owned by the caller (normally [proj.memorchess.axl.ui.App]) so that its
 * lifecycle stays composition scoped. Descendants that need to issue navigation actions read the
 * [Navigator] from [LocalNavigator] instead of going through this parameter.
 *
 * Screen swaps are **instant** on purpose: Training and Explore each host a full 64-tile board, and
 * letting `NavHost` cross-animate two of them keeps both composed at once — the source of visible
 * jank. The transition motion is supplied entirely by the cheap, draw-phase
 * [proj.memorchess.axl.ui.components.navigation.KineticWipeOverlay] accent sweep painted over the
 * swap, so only one screen is ever composed.
 */
@Composable
fun Router(navController: NavHostController, modifier: Modifier = Modifier) {
  NavHost(
    navController = navController,
    startDestination = Route.TrainingRoute,
    modifier = modifier,
    enterTransition = { EnterTransition.None },
    exitTransition = { ExitTransition.None },
    popEnterTransition = { EnterTransition.None },
    popExitTransition = { ExitTransition.None },
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
