package proj.memorchess.axl.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import androidx.window.core.layout.WindowSizeClass
import kotlin.time.Duration
import org.koin.compose.KoinApplication
import org.koin.dsl.koinConfiguration
import proj.memorchess.axl.core.config.MINIMUM_LOADING_TIME_SETTING
import proj.memorchess.axl.initKoinModules
import proj.memorchess.axl.ui.components.navigation.BottomNavigationBar
import proj.memorchess.axl.ui.components.navigation.KineticTopBar
import proj.memorchess.axl.ui.components.navigation.KineticTopBarNavItem
import proj.memorchess.axl.ui.components.navigation.NavigationBarItemContent
import proj.memorchess.axl.ui.layout.MainLayout
import proj.memorchess.axl.ui.pages.navigation.DelegateNavigator
import proj.memorchess.axl.ui.pages.navigation.LocalNavigator
import proj.memorchess.axl.ui.pages.navigation.Navigator
import proj.memorchess.axl.ui.pages.navigation.Route
import proj.memorchess.axl.ui.pages.navigation.Router
import proj.memorchess.axl.ui.theme.AppTheme

private const val VERSION_LABEL = "0.0.1 · MULTIPLATFORM"

/**
 * Top level entry point that starts Koin with the application's process scoped modules and renders
 * [App].
 */
@Composable
fun KoinStarterApp() {
  KoinApplication(configuration = koinConfiguration { modules(*initKoinModules()) }) { App() }
}

/**
 * Renders the top level chrome (navigation bars, theme) and the [Router].
 *
 * The [Navigator] is created here from a [rememberNavController] and provided to descendants via
 * [LocalNavigator]. Keeping it composition scoped avoids the initialization order races that
 * occurred when the NavController was stored in a Koin singleton.
 *
 * @param onNavHostReady Suspending callback invoked once the [Navigator] is ready. Used by the
 *   wasmJs entry point to bind the browser history to the NavHostController.
 */
@Composable
fun App(onNavHostReady: suspend (Navigator) -> Unit = {}) {
  MINIMUM_LOADING_TIME_SETTING.setValue(Duration.ZERO)
  val navController = rememberNavController()
  val navigator = remember(navController) { DelegateNavigator(navController) }
  CompositionLocalProvider(LocalNavigator provides navigator) {
    AppTheme {
      val navBackStackEntry by navigator.currentBackStackEntryAsState()
      val currentRoute =
        navBackStackEntry?.destination?.route?.substringBefore("?")
          ?: Route.TrainingRoute.getLabel()
      val windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass
      val isWide by
        remember(windowSizeClass) {
          derivedStateOf {
            windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND)
          }
        }
      val sortedNavItems = remember { NavigationBarItemContent.entries.sortedBy { it.index } }
      val navItems =
        remember(sortedNavItems) {
          sortedNavItems.map { entry ->
            KineticTopBarNavItem(
              route = entry.destination.getLabel(),
              label = entry.destination.getLabel().uppercase(),
              number = (entry.index + 1).toString().padStart(2, '0'),
            )
          }
        }
      MainLayout(
        topBar = {
          KineticTopBar(
            navItems = if (isWide) navItems else emptyList(),
            activeRoute = currentRoute,
            onNavigate = { route ->
              sortedNavItems
                .firstOrNull { it.destination.getLabel() == route }
                ?.let { entry -> navigator.navigateTo(entry.destination) }
            },
            versionLabel = VERSION_LABEL,
            compact = !isWide,
          )
        },
        bottomBar = { BottomNavigationBar(currentRoute, NavigationBarItemContent.entries) },
      ) { innerPadding ->
        Router(modifier = Modifier.padding(innerPadding), navController = navController)
      }
      LaunchedEffect(navigator) { onNavHostReady(navigator) }
    }
  }
}
