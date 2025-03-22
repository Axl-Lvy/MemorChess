package proj.ankichess.axl

import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import org.jetbrains.compose.resources.painterResource
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
          navBackStackEntry?.destination?.route?.substringBefore("?")
            ?: Destination.MANAGE_SAVES.name
        BottomNavigation() {
          BottomBarItem.entries.forEach { item ->
            val isSelected by
              remember(currentRoute) { derivedStateOf { currentRoute == item.destination.name } }
            BottomNavigationItem(
              selected = isSelected,
              icon = {
                Icon(painter = painterResource(item.icon), contentDescription = item.label)
              },
              onClick = { navController.navigate(item.destination.name) },
            )
          }
        }
      },
    )
  }
}
