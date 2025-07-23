package proj.memorchess.axl.ui.components.navigation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController

/** Bottom navigation bar */
@Composable
fun BottomNavigationBar(
  currentRoute: String,
  navController: NavHostController,
  items: Collection<NavigationBarItemContent>,
  modifier: Modifier = Modifier,
) {
  NavigationBar(modifier = modifier, windowInsets = WindowInsets(0, 0, 0, 0)) {
    items
      .sortedBy { it.index }
      .forEach { item ->
        val isSelected by
          remember(currentRoute) { derivedStateOf { currentRoute == item.destination.name } }
        NavigationBarItem(
          selected = isSelected,
          icon = item.icon,
          label = { Text(item.destination.label) },
          onClick = { navController.navigate(item.destination.name) },
        )
      }
  }
}
