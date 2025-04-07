package proj.ankichess.axl

import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
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
      isFloatingActionButtonDocked = true,
      floatingActionButton = { CenterButton(navController = navController) },
    )
  }
}

@Composable
private fun TopBar() {
  TopAppBar(
    title = { Text(text = "AnkiChess", color = Color.White) },
    navigationIcon = {
      IconButton(onClick = { /* TODO: Handle navigation icon click */ }) {
        Icon(FeatherIcons.Menu, contentDescription = "Menu", tint = Color.White)
      }
    },
    backgroundColor = MaterialTheme.colors.primary,
    elevation = 4.dp,
  )
}
