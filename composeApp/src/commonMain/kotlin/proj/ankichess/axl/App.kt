package proj.ankichess.axl

import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import proj.ankichess.axl.navigation.Destination
import proj.ankichess.axl.navigation.Router
import proj.ankichess.axl.navigation.bottomBar.BottomBar
import proj.ankichess.axl.navigation.bottomBar.CenterButton

@Composable
fun App() {
  MaterialTheme {
    val navController = rememberNavController()
    Scaffold(
      content = { Router(navController = navController) },
      bottomBar = {
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute =
          navBackStackEntry?.destination?.route?.substringBefore("?") ?: Destination.EXPLORE.name
        BottomBar(currentRoute, navController)
      },
      floatingActionButtonPosition = FabPosition.Center,
      isFloatingActionButtonDocked = true,
      floatingActionButton = { CenterButton(navController = navController) },
    )
  }
}
