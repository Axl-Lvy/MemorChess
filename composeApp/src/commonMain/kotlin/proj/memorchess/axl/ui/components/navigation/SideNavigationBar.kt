package proj.memorchess.axl.ui.components.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import proj.memorchess.axl.ui.pages.navigation.LocalNavigator

/**
 * App-level side navigation rail. Delegates to [KineticSideNav] and wires [LocalNavigator]; the
 * wrapper exists so call sites in `App.kt` remain unchanged.
 */
@Deprecated("Top bar replaces the side nav in the Kinetic design. Kept for potential future use.")
@Composable
fun SideNavigationBar(
  currentRoute: String,
  items: Collection<NavigationBarItemContent>,
  modifier: Modifier = Modifier,
) {
  val navigator = LocalNavigator.current
  val sorted = items.sortedBy { it.index }
  KineticSideNav(
    items = sorted,
    currentRoute = currentRoute,
    onSelect = { navigator.navigateTo(it.destination) },
    modifier = modifier,
  )
}
