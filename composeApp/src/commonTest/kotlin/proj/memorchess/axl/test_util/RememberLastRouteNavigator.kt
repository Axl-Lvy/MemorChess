package proj.memorchess.axl.test_util

import proj.memorchess.axl.ui.pages.navigation.Route

/** Navigator that remembers the last route navigated to. */
class RememberLastRouteNavigator : NoOpNavigator() {
  var lastRoute: Route? = null

  override fun navigateTo(route: Route) {
    lastRoute = route
  }
}
