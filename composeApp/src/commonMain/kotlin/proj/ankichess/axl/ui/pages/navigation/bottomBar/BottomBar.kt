package proj.ankichess.axl.ui.pages.navigation.bottomBar

import androidx.compose.material.BottomNavigation
import androidx.compose.material.BottomNavigationItem
import androidx.compose.material.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.navigation.NavHostController

@Composable
fun BottomBar(currentRoute: String, navController: NavHostController) {
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
}
