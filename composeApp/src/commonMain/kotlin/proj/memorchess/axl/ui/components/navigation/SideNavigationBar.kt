package proj.memorchess.axl.ui.components.navigation

import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController

/** Side navigation bar */
@Composable
fun SideNavigationBar(
  currentRoute: String,
  navController: NavHostController,
  items: Collection<NavigationBarItemContent>,
  modifier: Modifier,
) {
  NavigationRail(modifier = modifier) {
    items.forEach { item ->
      val isSelected by
        remember(currentRoute) { derivedStateOf { currentRoute == item.destination.name } }
      NavigationRailItem(
        selected = isSelected,
        icon = item.icon,
        label = { Text(item.destination.label) },
        onClick = { navController.navigate(item.destination.name) },
      )
    }
  }
}
