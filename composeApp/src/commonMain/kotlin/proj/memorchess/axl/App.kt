package proj.memorchess.axl

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import proj.memorchess.axl.core.graph.nodes.NodeManager
import proj.memorchess.axl.ui.components.loading.LoadingWidget
import proj.memorchess.axl.ui.pages.navigation.Destination
import proj.memorchess.axl.ui.pages.navigation.Router
import proj.memorchess.axl.ui.pages.navigation.bottomBar.BottomBar

@Composable
fun App() {
  MaterialTheme {
    LoadingWidget({ NodeManager.resetCacheFromDataBase() }) {
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
}
