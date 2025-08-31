package proj.memorchess.axl.test_util

import proj.memorchess.axl.ui.pages.navigation.Route


/**
 * A navigator implementation that remembers the last route navigated to. Used for testing purposes.
 *
 * @constructor Create empty Remember last route navigator
 */
class RememberLastRouteNavigator : NoOpNavigator() {
  var lastRoute: Route? = null
  override fun navigateTo(route: Route) {
    lastRoute = route
  }
}