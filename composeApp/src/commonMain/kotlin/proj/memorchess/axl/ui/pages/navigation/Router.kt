package proj.memorchess.axl.ui.pages.navigation

import androidx.compose.animation.AnimatedVisibilityScope
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
import proj.memorchess.axl.ui.pages.Today
import proj.memorchess.axl.ui.pages.Training
import proj.memorchess.axl.ui.theme.KineticMotion

/** A route string's ordinal and whether it is one of the bottom nav tab destinations. */
private data class RouteClass(val ordinal: Int, val isTabRoute: Boolean)

/**
 * Classifies a raw destination route against [NavigationBarItemContent]. The repertoire viewer
 * shares Library's ordinal without being a tab; any other unknown route takes Training's ordinal.
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
 * Ordinal of this destination along the navigation bar, which sets the wipe direction. Matched on
 * the route string, so route arguments and package qualifiers are tolerated.
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
 * Resolves the tab [Route] named by a `location.hash` value (e.g. `"#library"`), falling back to
 * [Route.TodayRoute] for anything else.
 */
internal fun routeFromHash(hash: String): Route {
  val serialName = hash.removePrefix("#").substringBefore('?')
  return NavigationBarItemContent.entries
    .map { it.destination }
    .firstOrNull { it.getLabel().equals(serialName, ignoreCase = true) } ?: Route.TodayRoute
}

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
 * Full-size [Box] around [content], wipe-revealed per [revealFromRight] unless
 * [tabToTabTransition], whose motion [tabAwareEnter]/[tabAwareExit] already handle.
 */
@Composable
private fun AnimatedVisibilityScope.RevealBox(
  tabToTabTransition: Boolean,
  revealFromRight: Boolean,
  content: @Composable () -> Unit,
) {
  val revealModifier = if (tabToTabTransition) Modifier else wipeReveal(revealFromRight)
  Box(modifier = Modifier.fillMaxSize().then(revealModifier)) { content() }
}

/**
 * Renders the navigation graph, wiping between screens with a curtain seam except between two
 * bottom nav tabs, which slide and fade.
 */
@Composable
fun Router(
  navController: NavHostController,
  modifier: Modifier = Modifier,
  startDestination: Route = Route.TodayRoute,
) {
  val backStackEntry by navController.currentBackStackEntryAsState()
  val currentRoute = backStackEntry?.destination?.route.orEmpty()
  val currentOrdinal = backStackEntry?.routeOrdinal() ?: 1
  val currentEntryId = backStackEntry?.id
  var previousRoute by remember { mutableStateOf(currentRoute) }
  var previousOrdinal by remember { mutableStateOf(currentOrdinal) }
  var previousEntryId by remember { mutableStateOf(currentEntryId) }
  var revealFromRight by remember { mutableStateOf(true) }
  var tabToTabTransition by remember { mutableStateOf(false) }
  // Frozen on ordinal change so the direction stays stable for the whole wipe. Library and
  // RepertoireView share an ordinal, so moving between them keeps the last direction.
  if (currentOrdinal != previousOrdinal) {
    revealFromRight = currentOrdinal > previousOrdinal
    previousOrdinal = currentOrdinal
  }
  // Keyed on the entry id, not the route: two instances of one destination share a route string.
  if (currentEntryId != previousEntryId) {
    tabToTabTransition = isTabToTabTransition(previousRoute, currentRoute)
    previousRoute = currentRoute
    previousEntryId = currentEntryId
  }

  NavHost(
    navController = navController,
    startDestination = startDestination,
    modifier = modifier,
    enterTransition = { tabAwareEnter(initialState, targetState) },
    exitTransition = { tabAwareExit(initialState, targetState) },
    popEnterTransition = { tabAwareEnter(initialState, targetState) },
    popExitTransition = { tabAwareExit(initialState, targetState) },
  ) {
    composable<Route.TodayRoute> { RevealBox(tabToTabTransition, revealFromRight) { Today() } }
    composable<Route.TrainingRoute> {
      val repertoireId = it.toRoute<Route.TrainingRoute>().repertoireId
      RevealBox(tabToTabTransition, revealFromRight) { Training(repertoireId) }
    }
    composable<Route.LibraryRoute> {
      RevealBox(tabToTabTransition, revealFromRight) { RepertoireLibrary() }
    }
    composable<Route.RepertoireViewRoute> {
      val repertoireId = it.toRoute<Route.RepertoireViewRoute>().repertoireId
      RevealBox(tabToTabTransition, revealFromRight) { RepertoireView(repertoireId) }
    }
    composable<Route.SettingsRoute> {
      RevealBox(tabToTabTransition, revealFromRight) { Settings() }
    }
    composable<Route.ExploreRoute> {
      val route = it.toRoute<Route.ExploreRoute>()
      RevealBox(tabToTabTransition, revealFromRight) {
        Explore(
          route.position?.let { p -> PositionKey.validateAndCreateOrNull(p) },
          route.repertoireId,
        )
      }
    }
  }
}
