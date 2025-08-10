package proj.memorchess.axl

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import kotlin.time.Duration
import org.koin.compose.KoinApplication
import proj.memorchess.axl.core.config.MINIMUM_LOADING_TIME_SETTING
import proj.memorchess.axl.ui.components.navigation.BottomNavigationBar
import proj.memorchess.axl.ui.components.navigation.NavigationBarItemContent
import proj.memorchess.axl.ui.components.navigation.SideNavigationBar
import proj.memorchess.axl.ui.layout.MainLayout
import proj.memorchess.axl.ui.pages.navigation.Route
import proj.memorchess.axl.ui.pages.navigation.Router
import proj.memorchess.axl.ui.theme.AppTheme

@Composable
fun KoinStarterApp() {
  KoinApplication(application = { modules(*initKoinModules()) }) { App() }
}

@Composable
fun App(onNavHostReady: suspend (NavController) -> Unit = {}) {
  MINIMUM_LOADING_TIME_SETTING.setValue(Duration.ZERO)
  AppTheme {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute =
      navBackStackEntry?.destination?.route?.substringBefore("?") ?: Route.TrainingRoute.getLabel()
    MainLayout(
      sideBar = {
        SideNavigationBar(currentRoute, navController, NavigationBarItemContent.entries)
      },
      bottomBar = {
        BottomNavigationBar(currentRoute, navController, NavigationBarItemContent.entries)
      },
    ) { innerPadding ->
      Router(navController = navController, modifier = Modifier.padding(innerPadding))
    }
    LaunchedEffect(navController) { onNavHostReady(navController) }
  }
}
