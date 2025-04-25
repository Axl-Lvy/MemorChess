package proj.ankichess.axl.ui.pages.navigation.bottomBar

import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.navigation.NavHostController

@Composable
fun BottomBar(currentRoute: String, navController: NavHostController) {
  NavigationBar {
    BottomBarItem.entries
      .sortedBy { it.index }
      .forEach { item ->
        val isSelected by
          remember(currentRoute) { derivedStateOf { currentRoute == item.destination.name } }
        NavigationBarItem(
          selected = isSelected,
          icon = { Icon(item.icon, contentDescription = item.label) },
          onClick = { navController.navigate(item.destination.name) },
        )
      }
  }
}
