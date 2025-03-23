package proj.ankichess.axl

import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import proj.ankichess.axl.navigation.BottomBarItem
import proj.ankichess.axl.navigation.Destination
import proj.ankichess.axl.navigation.Router

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
        BottomNavigation() {
          BottomBarItem.entries
            .sortedBy { it.index }
            .forEach { item ->
              val isSelected by
                remember(currentRoute) { derivedStateOf { currentRoute == item.destination.name } }
              BottomNavigationItem(
                selected = isSelected,
                icon = { Icon(item.icon, contentDescription = item.label) },
                onClick = { navController.navigate(item.destination.name) },
              )
            }
        }
      },
    )
  }
}
