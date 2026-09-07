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
 * @param startRoute Initial route ([Router]'s `startDestination`). Defaults to [Route.TodayRoute];
 *   the wasmJs entry point overrides it from the URL hash so a page refresh reopens the tab the
 *   user was on.
 * @param onNavHostReady Suspending callback invoked once the [Navigator] is ready. Used by the
 *   wasmJs entry point to bind the browser history to the NavHostController.
 */
@Composable
fun App(startRoute: Route = Route.TodayRoute, onNavHostReady: suspend (Navigator) -> Unit = {}) {
  MINIMUM_LOADING_TIME_SETTING.setValue(Duration.ZERO)
  val syncEngine: SyncEngine = koinInject()
  LaunchedEffect(Unit) { syncEngine.start() }
  val navController = rememberNavController()
  val navigator = remember(navController) { DelegateNavigator(navController) }
  CompositionLocalProvider(LocalNavigator provides navigator) {
    AppTheme {
      val navBackStackEntry by navigator.currentBackStackEntryAsState()
      val currentRoute =
        navBackStackEntry?.destination?.route?.substringBefore("?") ?: Route.TodayRoute.getLabel()
      val sortedNavItems = remember { NavigationBarItemContent.entries.sortedBy { it.index } }
      // Same tags as the bottom bar: MainLayout renders the side bar, the desktop rail and the
      // bottom bar in mutually exclusive branches, so they can never collide in one composition.
      val railItemModifier: (NavigationBarItemContent) -> Modifier = {
        Modifier.testTag("bottom_navigation_bar_item_${it.destination.getLabel()}")
      }
      // Compose's first frame can paint before these resources resolve — a blank flash of text
      // across the whole screen, not just one component, since every Text here reads a font from
      // the same three families. Gate the whole tree behind them instead of chasing the flash
      // component by component, the same idea PiecePainters already applies to chess-piece images.
      val fontFamilyResolver = LocalFontFamilyResolver.current
      val fontFamilies = LocalKineticTypography.current.fontFamilies()
      LoadingWidget(
        suspendingFunction = { fontFamilies.forEach { fontFamilyResolver.preload(it) } }
      ) {
        MainLayout(
          topBar = {
            // Only ever rendered on narrow-portrait (MainLayout's desktop-rail branch replaces the
            // top bar entirely on a wide window), so this is always the compact, no-nav-items bar.
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
