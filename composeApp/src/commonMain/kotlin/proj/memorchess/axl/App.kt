package proj.memorchess.axl

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import compose.icons.FeatherIcons
import compose.icons.feathericons.Menu
import proj.memorchess.axl.core.graph.nodes.NodeManager
import proj.memorchess.axl.ui.components.loading.LoadingWidget
import proj.memorchess.axl.ui.pages.navigation.Destination
import proj.memorchess.axl.ui.pages.navigation.Router
import proj.memorchess.axl.ui.pages.navigation.bottomBar.BottomBar
import proj.memorchess.axl.ui.pages.navigation.bottomBar.CenterButton

@Composable
fun App() {
  MaterialTheme {
    LoadingWidget({ NodeManager.resetCacheFromDataBase() }) {
      val navController = rememberNavController()
      Scaffold(
        topBar = { TopBar() },
        bottomBar = {
          val navBackStackEntry by navController.currentBackStackEntryAsState()
          val currentRoute =
            navBackStackEntry?.destination?.route?.substringBefore("?") ?: Destination.EXPLORE.name
          BottomBar(currentRoute, navController)
        },
        floatingActionButtonPosition = FabPosition.Center,
        floatingActionButton = { CenterButton(navController = navController) },
      ) { innerPadding ->
        Router(navController = navController, modifier = Modifier.padding(innerPadding))
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopBar() {
  CenterAlignedTopAppBar(
    title = { Text(text = "MemorChess", color = Color.White) },
    navigationIcon = {
      IconButton(onClick = {}) {
        Icon(FeatherIcons.Menu, contentDescription = "Menu", tint = Color.White)
      }
    },
  )
}
