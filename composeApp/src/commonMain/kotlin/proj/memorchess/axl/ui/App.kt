package proj.memorchess.axl.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.navigation.compose.rememberNavController
import kotlin.time.Duration
import org.koin.compose.KoinApplication
import org.koin.compose.koinInject
import org.koin.dsl.koinConfiguration
import proj.memorchess.axl.core.config.MINIMUM_LOADING_TIME_SETTING
import proj.memorchess.axl.core.sync.SyncEngine
import proj.memorchess.axl.initKoinModules
import proj.memorchess.axl.ui.components.loading.LoadingWidget
import proj.memorchess.axl.ui.components.navigation.BottomNavigationBar
import proj.memorchess.axl.ui.components.navigation.KineticDesktopRail
import proj.memorchess.axl.ui.components.navigation.KineticSideBar
import proj.memorchess.axl.ui.components.navigation.KineticTopBar
import proj.memorchess.axl.ui.components.navigation.NavigationBarItemContent
import proj.memorchess.axl.ui.layout.MainLayout
import proj.memorchess.axl.ui.pages.navigation.DelegateNavigator
import proj.memorchess.axl.ui.pages.navigation.LocalNavigator
import proj.memorchess.axl.ui.pages.navigation.Navigator
import proj.memorchess.axl.ui.pages.navigation.Route
import proj.memorchess.axl.ui.pages.navigation.Router
import proj.memorchess.axl.ui.theme.AppTheme
import proj.memorchess.axl.ui.theme.LocalKineticTypography
import proj.memorchess.axl.ui.theme.fontFamilies

/** Top level entry point that starts Koin with the application modules and renders [App]. */
@Composable
fun KoinStarterApp() {
  KoinApplication(configuration = koinConfiguration { modules(*initKoinModules()) }) { App() }
}

/**
 * Renders the top level chrome and the [Router], providing the [Navigator] via [LocalNavigator].
 */
@Composable
fun App(startRoute: Route = Route.TodayRoute, onNavHostReady: suspend (Navigator) -> Unit = {}) {
  MINIMUM_LOADING_TIME_SETTING.setValue(Duration.ZERO)
  val syncEngine: SyncEngine = koinInject()
  LaunchedEffect(Unit) { syncEngine.start() }
  // Acknowledging on resume rather than at the next heartbeat keeps the tombstone collection
  // watermark moving. Compose Multiplatform drives this lifecycle on every platform.
  LifecycleResumeEffect(Unit) {
    syncEngine.onAppForeground()
    onPauseOrDispose {}
  }
  // Composition scoped: a NavController held in a Koin singleton races on initialization.
  val navController = rememberNavController()
  val navigator = remember(navController) { DelegateNavigator(navController) }
  CompositionLocalProvider(LocalNavigator provides navigator) {
    AppTheme {
      val navBackStackEntry by navigator.currentBackStackEntryAsState()
      val currentRoute =
        navBackStackEntry?.destination?.route?.substringBefore("?") ?: Route.TodayRoute.getLabel()
      val sortedNavItems = remember { NavigationBarItemContent.entries.sortedBy { it.index } }
      // Reuses the bottom bar's tags: MainLayout renders the bars in mutually exclusive branches.
      val railItemModifier: (NavigationBarItemContent) -> Modifier = {
        Modifier.testTag("bottom_navigation_bar_item_${it.destination.getLabel()}")
      }
      // The first frame can paint before fonts resolve, flashing blank text across the screen.
      val fontFamilyResolver = LocalFontFamilyResolver.current
      val fontFamilies = LocalKineticTypography.current.fontFamilies()
      LoadingWidget(
        suspendingFunction = { fontFamilies.forEach { fontFamilyResolver.preload(it) } }
      ) {
        MainLayout(
          topBar = {
            // Only rendered on narrow portrait, so always the compact bar without nav items.
            KineticTopBar(
              navItems = emptyList(),
              activeRoute = currentRoute,
              onNavigate = {},
              compact = true,
            )
          },
          bottomBar = { BottomNavigationBar(currentRoute, NavigationBarItemContent.entries) },
          sideBar = {
            KineticSideBar(
              items = sortedNavItems,
              currentRoute = currentRoute,
              onSelect = { navigator.navigateTo(it.destination) },
              itemModifier = railItemModifier,
            )
          },
          desktopRail = {
            KineticDesktopRail(
              items = sortedNavItems,
              currentRoute = currentRoute,
              onSelect = { navigator.navigateTo(it.destination) },
              itemModifier = railItemModifier,
            )
          },
        ) { innerPadding ->
          Router(
            modifier = Modifier.padding(innerPadding),
            navController = navController,
            startDestination = startRoute,
          )
        }
      }
      LaunchedEffect(navigator) { onNavHostReady(navigator) }
    }
  }
}
