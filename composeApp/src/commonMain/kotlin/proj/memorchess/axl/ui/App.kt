package proj.memorchess.axl.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import kotlin.time.Duration
import org.koin.compose.KoinApplication
import org.koin.compose.koinInject
import proj.memorchess.axl.core.config.MINIMUM_LOADING_TIME_SETTING
import proj.memorchess.axl.initKoinModules
import proj.memorchess.axl.ui.components.navigation.BottomNavigationBar
import proj.memorchess.axl.ui.components.navigation.NavigationBarItemContent
import proj.memorchess.axl.ui.components.navigation.SideNavigationBar
import proj.memorchess.axl.ui.layout.MainLayout
import proj.memorchess.axl.ui.pages.navigation.Navigator
import proj.memorchess.axl.ui.pages.navigation.Route
import proj.memorchess.axl.ui.pages.navigation.Router
import proj.memorchess.axl.ui.theme.AppTheme

@Composable
fun KoinStarterApp() {
  val modules = initKoinModules() + initComposableModules()
  KoinApplication(application = { modules(*modules) }) { App() }
}

@Composable
fun App(navigator: Navigator = koinInject(), onNavHostReady: suspend (Navigator) -> Unit = {}) {
  MINIMUM_LOADING_TIME_SETTING.setValue(Duration.ZERO)
  AppTheme {
    val navBackStackEntry by navigator.currentBackStackEntryAsState()
    val currentRoute =
      navBackStackEntry?.destination?.route?.substringBefore("?") ?: Route.TrainingRoute.getLabel()
    MainLayout(
      sideBar = { SideNavigationBar(currentRoute, NavigationBarItemContent.entries) },
      bottomBar = { BottomNavigationBar(currentRoute, NavigationBarItemContent.entries) },
    ) { innerPadding ->
      Router(modifier = Modifier.padding(innerPadding))
    }
    LaunchedEffect(navigator) { onNavHostReady(navigator) }
  }
}
