package proj.memorchess.axl.ui.components.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import proj.memorchess.axl.ui.pages.navigation.LocalNavigator

/**
 * App-level bottom navigation bar. Delegates rendering to [KineticBottomNav] and wires the active
 * route into [LocalNavigator]; the wrapper exists so call sites in `App.kt` remain unchanged and
 * Android instrumented tests can keep referencing the `bottom_navigation_bar_item_*` test tags.
 */
@Composable
fun BottomNavigationBar(
  currentRoute: String,
  items: Collection<NavigationBarItemContent>,
  modifier: Modifier = Modifier,
) {
  val navigator = LocalNavigator.current
  val sorted = items.sortedBy { it.index }
  KineticBottomNav(
    items = sorted,
    currentRoute = currentRoute,
    onSelect = { navigator.navigateTo(it.destination) },
    modifier = modifier,
    itemModifier = { Modifier.testTag("bottom_navigation_bar_item_${it.destination.getLabel()}") },
  )
}
