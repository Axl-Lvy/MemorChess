package proj.memorchess.axl.ui.components.navigation

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import proj.memorchess.axl.ui.pages.navigation.LocalNavigator

/** Side navigation bar. */
@Composable
fun SideNavigationBar(
  currentRoute: String,
  items: Collection<NavigationBarItemContent>,
  modifier: Modifier = Modifier,
) {
  val navigator = LocalNavigator.current
  NavigationRail(modifier = modifier.fillMaxHeight()) {
    items.forEach { item ->
      val isSelected by
        remember(currentRoute) { derivedStateOf { currentRoute == item.destination.getLabel() } }
      NavigationRailItem(
        selected = isSelected,
        icon = item.icon,
        label = { Text(item.destination.getLabel()) },
        onClick = { navigator.navigateTo(item.destination) },
      )
    }
  }
}
