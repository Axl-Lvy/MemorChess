package proj.memorchess.axl.ui.components.navigation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import org.koin.compose.koinInject
import proj.memorchess.axl.ui.pages.navigation.Navigator

/** Bottom navigation bar */
@Composable
fun BottomNavigationBar(
  currentRoute: String,
  items: Collection<NavigationBarItemContent>,
  modifier: Modifier = Modifier,
  navigator: Navigator = koinInject(),
) {
  NavigationBar(modifier = modifier.fillMaxWidth(), windowInsets = WindowInsets(0, 0, 0, 0)) {
    items
      .sortedBy { it.index }
      .forEach { item ->
        val isSelected by
          remember(currentRoute) { derivedStateOf { currentRoute == item.destination.getLabel() } }
        NavigationBarItem(
          selected = isSelected,
          icon = item.icon,
          label = { Text(item.destination.getLabel()) },
          onClick = { navigator.navigateTo(item.destination) },
          modifier = Modifier.testTag("bottom_navigation_bar_item_${item.destination.getLabel()}"),
        )
      }
  }
}
