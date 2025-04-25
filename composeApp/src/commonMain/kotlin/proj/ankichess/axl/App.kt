package proj.ankichess.axl

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import compose.icons.FeatherIcons
import compose.icons.feathericons.Menu
import proj.ankichess.axl.ui.pages.navigation.Destination
import proj.ankichess.axl.ui.pages.navigation.Router
import proj.ankichess.axl.ui.pages.navigation.bottomBar.BottomBar
import proj.ankichess.axl.ui.pages.navigation.bottomBar.CenterButton

@Composable
fun App() {
  MaterialTheme {
    val navController = rememberNavController()
    Scaffold(
      content = { Router(navController = navController) },
      topBar = { TopBar() },
      bottomBar = {
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute =
          navBackStackEntry?.destination?.route?.substringBefore("?") ?: Destination.EXPLORE.name
        BottomBar(currentRoute, navController)
      },
      floatingActionButtonPosition = FabPosition.Center,
      floatingActionButton = { CenterButton(navController = navController) },
    )
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopBar() {
  TopAppBar(
    title = { Text(text = "AnkiChess", color = Color.White) },
    navigationIcon = {
      IconButton(onClick = { /* TODO: Handle navigation icon click */ }) {
        Icon(FeatherIcons.Menu, contentDescription = "Menu", tint = Color.White)
      }
    },
  )
}
