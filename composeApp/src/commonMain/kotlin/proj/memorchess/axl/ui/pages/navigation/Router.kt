package proj.memorchess.axl.ui.pages.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
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
import proj.memorchess.axl.ui.components.navigation.NavigationBarItemContent
import proj.memorchess.axl.ui.components.navigation.wipeReveal
import proj.memorchess.axl.ui.pages.Explore
import proj.memorchess.axl.ui.pages.RepertoireLibrary
import proj.memorchess.axl.ui.pages.RepertoireView
import proj.memorchess.axl.ui.pages.Settings
import proj.memorchess.axl.ui.pages.Training
import proj.memorchess.axl.ui.theme.KineticMotion

/** A route string's ordinal and whether it is one of the bottom nav tab destinations. */
private data class RouteClass(val ordinal: Int, val isTabRoute: Boolean)

/**
 * Classifies a raw destination route string against [NavigationBarItemContent], the authoritative
 * registry of bottom nav destinations. A route string added there is picked up here for free. The
 * repertoire viewer is not in that registry. It shares Library's ordinal for wipe direction only
 * and is not a tab route. Any other unrecognised destination falls back to Training's ordinal.
 */
private fun classifyRoute(route: String): RouteClass {
  val tabItem =
    NavigationBarItemContent.entries.firstOrNull {
      route.contains(it.destination.getLabel(), ignoreCase = true)
    }
  if (tabItem != null) return RouteClass(tabItem.index, isTabRoute = true)
  return if (route.contains("repertoireview", ignoreCase = true))
    RouteClass(NavigationBarItemContent.Library.index, isTabRoute = false)
  else RouteClass(NavigationBarItemContent.Training.index, isTabRoute = false)
}

/**
 * Ordinal of a destination along the navigation bar, per [NavigationBarItemContent]'s declared
 * order.
 *
 * Drives the direction of the screen transition: navigating toward a higher ordinal reveals the new
 * screen from the right, toward a lower one from the left. Matched against the destination route
 * string (which carries the route's [kotlinx.serialization.SerialName]) so it tolerates the
 * trailing `?position=…` argument on the explore route and any package qualifier. Defaults to
 * Training's ordinal for any unrecognised destination.
 */
internal fun NavBackStackEntry.routeOrdinal(): Int =
  classifyRoute(destination.route.orEmpty()).ordinal

/**
 * Whether a navigation between [fromRoute] and [toRoute] (raw destination route strings) stays
 * within the bottom nav tab destinations declared by [NavigationBarItemContent].
 */
internal fun isTabToTabTransition(fromRoute: String, toRoute: String): Boolean =
  classifyRoute(fromRoute).isTabRoute && classifyRoute(toRoute).isTabRoute

/**
 * Enter transition for a push between two back stack entries: [KineticMotion.tabEnter]'s slide and
 * fade when both ends are bottom nav tabs, otherwise the plain curtain [KineticMotion.holdEnter].
 */
private fun tabAwareEnter(from: NavBackStackEntry, to: NavBackStackEntry): EnterTransition =
  if (isTabToTabTransition(from.destination.route.orEmpty(), to.destination.route.orEmpty()))
    KineticMotion.tabEnter(fromRight = to.routeOrdinal() > from.routeOrdinal())
  else KineticMotion.holdEnter()

/**
 * Exit transition mirroring [tabAwareEnter]: a fade alone between two tabs, otherwise the plain
 * curtain hold.
 */
private fun tabAwareExit(from: NavBackStackEntry, to: NavBackStackEntry): ExitTransition =
  if (isTabToTabTransition(from.destination.route.orEmpty(), to.destination.route.orEmpty()))
    KineticMotion.tabExit()
  else KineticMotion.holdExit()

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
 * navigating toward a higher-ordinal destination and left-to-right otherwise. Transitions between
 * two bottom nav tabs skip the curtain entirely: they use [KineticMotion.tabEnter] and
 * [KineticMotion.tabExit] instead, sliding and fading in while fading out alone. Every other
 * transition, a push onto or a pop off [Route.RepertoireViewRoute], keeps the curtain unchanged.
 */
@Composable
fun Router(navController: NavHostController, modifier: Modifier = Modifier) {
  val backStackEntry by navController.currentBackStackEntryAsState()
  val currentRoute = backStackEntry?.destination?.route.orEmpty()
  val currentOrdinal = backStackEntry?.routeOrdinal() ?: 1
  val currentEntryId = backStackEntry?.id
  var previousRoute by remember { mutableStateOf(currentRoute) }
  var previousOrdinal by remember { mutableStateOf(currentOrdinal) }
  var previousEntryId by remember { mutableStateOf(currentEntryId) }
  var revealFromRight by remember { mutableStateOf(true) }
  var tabToTabTransition by remember { mutableStateOf(false) }
  // Freeze the direction at the moment the ordinal changes so it stays stable for the whole wipe.
  // Library and RepertoireView share an ordinal, so this does not recompute between them and keeps
  // whatever direction the push that last changed the ordinal set.
  if (currentOrdinal != previousOrdinal) {
    revealFromRight = currentOrdinal > previousOrdinal
    previousOrdinal = currentOrdinal
  }
  // Freeze separately, keyed on the back stack entry id rather than the route string. A route
  // string is a pattern shared by every instance of a destination (e.g. two different Explore
  // positions), so keying this on the route would leave it stale across a push between two
  // instances of the same destination.
  if (currentEntryId != previousEntryId) {
    tabToTabTransition = isTabToTabTransition(previousRoute, currentRoute)
    previousRoute = currentRoute
    previousEntryId = currentEntryId
  }

  NavHost(
    navController = navController,
    startDestination = Route.TrainingRoute.DEFAULT,
    modifier = modifier,
    enterTransition = { tabAwareEnter(initialState, targetState) },
    exitTransition = { tabAwareExit(initialState, targetState) },
    popEnterTransition = { tabAwareEnter(initialState, targetState) },
    popExitTransition = { tabAwareExit(initialState, targetState) },
  ) {
    composable<Route.TrainingRoute> {
      val repertoireId = it.toRoute<Route.TrainingRoute>().repertoireId
      Box(
        modifier =
          Modifier.fillMaxSize()
            .then(if (tabToTabTransition) Modifier else wipeReveal(revealFromRight))
      ) {
        Training(repertoireId)
      }
    }
    composable<Route.LibraryRoute> {
      Box(
        modifier =
          Modifier.fillMaxSize()
            .then(if (tabToTabTransition) Modifier else wipeReveal(revealFromRight))
      ) {
        RepertoireLibrary()
      }
    }
    composable<Route.RepertoireViewRoute> {
      val repertoireId = it.toRoute<Route.RepertoireViewRoute>().repertoireId
      Box(
        modifier =
          Modifier.fillMaxSize()
            .then(if (tabToTabTransition) Modifier else wipeReveal(revealFromRight))
      ) {
        RepertoireView(repertoireId)
      }
    }
    composable<Route.SettingsRoute> {
      Box(
        modifier =
          Modifier.fillMaxSize()
            .then(if (tabToTabTransition) Modifier else wipeReveal(revealFromRight))
      ) {
        Settings()
      }
    }
    composable<Route.ExploreRoute> {
      val route = it.toRoute<Route.ExploreRoute>()
      Box(
        modifier =
          Modifier.fillMaxSize()
            .then(if (tabToTabTransition) Modifier else wipeReveal(revealFromRight))
      ) {
        Explore(
          route.position?.let { p -> PositionKey.validateAndCreateOrNull(p) },
          route.repertoireId,
        )
      }
    }
  }
}
