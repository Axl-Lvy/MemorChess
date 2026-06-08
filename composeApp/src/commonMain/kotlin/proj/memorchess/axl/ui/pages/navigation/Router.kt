package proj.memorchess.axl.ui.pages.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
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
import proj.memorchess.axl.ui.theme.KineticMotion

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
 * True when the transition moves toward a higher-ordinal destination (or stays on the same one).
 */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.isForward(): Boolean =
  targetState.routeOrdinal() >= initialState.routeOrdinal()

/**
 * Renders the navigation graph.
 *
 * The [navController] is owned by the caller (normally [proj.memorchess.axl.ui.App]) so that its
 * lifecycle stays composition scoped. Descendants that need to issue navigation actions read the
 * [Navigator] from [LocalNavigator] instead of going through this parameter.
 *
 * Screen transitions use the Kinetic directional axis wipe ([KineticMotion.axisWipeEnter] /
 * [KineticMotion.axisWipeExit]); the direction follows the tab ordinal so moving Explore → Settings
 * wipes one way and the reverse wipes the other.
 */
@Composable
fun Router(navController: NavHostController, modifier: Modifier = Modifier) {
  NavHost(
    navController = navController,
    startDestination = Route.TrainingRoute,
    modifier = modifier,
    enterTransition = { KineticMotion.axisWipeEnter(isForward()) },
    exitTransition = { KineticMotion.axisWipeExit(isForward()) },
    popEnterTransition = { KineticMotion.axisWipeEnter(isForward()) },
    popExitTransition = { KineticMotion.axisWipeExit(isForward()) },
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
