package proj.memorchess.axl

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import kotlin.time.Duration
import org.koin.core.context.startKoin
import proj.memorchess.axl.core.config.MINIMUM_LOADING_TIME_SETTING
import proj.memorchess.axl.ui.pages.navigation.Destination
import proj.memorchess.axl.ui.pages.navigation.Router
import proj.memorchess.axl.ui.pages.navigation.bottomBar.BottomBar
import proj.memorchess.axl.ui.theme.AppTheme

@Composable
fun App() {
  startKoin { modules(*initKoinModules()) }
  MINIMUM_LOADING_TIME_SETTING.setValue(Duration.ZERO)
  AppTheme {
    val navController = rememberNavController()
    Scaffold(
      bottomBar = {
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute =
          navBackStackEntry?.destination?.route?.substringBefore("?") ?: Destination.EXPLORE.name
        BottomBar(currentRoute, navController)
      },
      floatingActionButtonPosition = FabPosition.Center,
    ) { innerPadding ->
      Router(navController = navController, modifier = Modifier.padding(innerPadding))
    }
  }
}
