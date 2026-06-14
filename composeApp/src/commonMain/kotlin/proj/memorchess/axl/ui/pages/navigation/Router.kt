package proj.memorchess.axl.ui.pages.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.toRoute
import proj.memorchess.axl.core.data.PositionKey
import proj.memorchess.axl.ui.components.navigation.wipeReveal
import proj.memorchess.axl.ui.pages.Explore
import proj.memorchess.axl.ui.pages.RepertoireLibrary
import proj.memorchess.axl.ui.pages.RepertoireView
import proj.memorchess.axl.ui.pages.Settings
import proj.memorchess.axl.ui.pages.Training
import proj.memorchess.axl.ui.theme.KineticMotion

/**
 * Ordinal of a destination along the navigation bar (Explore `0`, Training `1`, Library `2`,
 * Settings `3`).
 *
 * Drives the direction of the screen transition: navigating toward a higher ordinal reveals the new
 * screen from the right, toward a lower one from the left. Matched against the destination route
 * string (which carries the route's [kotlinx.serialization.SerialName]) so it tolerates the
 * trailing `?position=…` argument on the explore route and any package qualifier. Defaults to
 * Training's ordinal for any unrecognised destination.
 */
internal fun NavBackStackEntry.routeOrdinal(): Int {
  val route = destination.route.orEmpty().lowercase()
  return when {
    route.contains("explore") -> 0
    route.contains("training") -> 1
    // The viewer shares the library ordinal so the wipe direction stays consistent. Its route
    // string ("repertoireview") is disjoint from "library", so it needs its own branch.
    route.contains("repertoireview") -> 2
    route.contains("library") -> 2
    route.contains("settings") -> 3
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
 * Screen transitions are a two-panel curtain wipe: both the outgoing and incoming screens are kept
 * composed for the transition ([KineticMotion.holdEnter] / [holdExit] hold them on screen with no
 * visible fade) and each is clipped to its side of a moving accent seam by
 * [proj.memorchess.axl.ui.components.navigation.wipeReveal]. The seam travels right-to-left when
 * navigating toward a higher-ordinal destination and left-to-right otherwise.
 */
@Composable
fun Router(navController: NavHostController, modifier: Modifier = Modifier) {
  val backStackEntry by navController.currentBackStackEntryAsState()
  val currentOrdinal = backStackEntry?.routeOrdinal() ?: 1
  var previousOrdinal by remember { mutableStateOf(currentOrdinal) }
  var revealFromRight by remember { mutableStateOf(true) }
  // Freeze the direction at the moment the route changes so it stays stable for the whole wipe.
  if (currentOrdinal != previousOrdinal) {
    revealFromRight = currentOrdinal > previousOrdinal
    previousOrdinal = currentOrdinal
  }

  NavHost(
    navController = navController,
    startDestination = Route.TrainingRoute,
    modifier = modifier,
    enterTransition = { KineticMotion.holdEnter() },
    exitTransition = { KineticMotion.holdExit() },
    popEnterTransition = { KineticMotion.holdEnter() },
    popExitTransition = { KineticMotion.holdExit() },
  ) {
    composable<Route.TrainingRoute> {
      Box(modifier = Modifier.fillMaxSize().then(wipeReveal(revealFromRight))) { Training() }
    }
    composable<Route.LibraryRoute> {
      Box(modifier = Modifier.fillMaxSize().then(wipeReveal(revealFromRight))) {
        RepertoireLibrary()
      }
    }
    composable<Route.RepertoireViewRoute> {
      val repertoireId = it.toRoute<Route.RepertoireViewRoute>().repertoireId
      Box(modifier = Modifier.fillMaxSize().then(wipeReveal(revealFromRight))) {
        RepertoireView(repertoireId)
      }
    }
    composable<Route.SettingsRoute> {
      Box(modifier = Modifier.fillMaxSize().then(wipeReveal(revealFromRight))) { Settings() }
    }
    composable<Route.ExploreRoute> {
      val position = it.toRoute<Route.ExploreRoute>().position
      Box(modifier = Modifier.fillMaxSize().then(wipeReveal(revealFromRight))) {
        Explore(position?.let { p -> PositionKey.validateAndCreateOrNull(p) })
      }
    }
  }
}
