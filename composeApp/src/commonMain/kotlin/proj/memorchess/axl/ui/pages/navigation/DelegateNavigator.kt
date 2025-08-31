package proj.memorchess.axl.ui.pages.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState

/**
 * A navigator implementation that delegates navigation actions to a NavHostController.
 *
 * @property navController The NavHostController to delegate navigation actions to.
 */
class DelegateNavigator(val navController: NavHostController) : Navigator {
  override fun navigateTo(route: Route) {
    navController.navigate(route)
  }

  @Composable
  override fun currentBackStackEntryAsState(): State<NavBackStackEntry?> {
    return navController.currentBackStackEntryAsState()
  }

  override suspend fun callDelegate(delegate: suspend (NavHostController) -> Unit) {
    delegate(navController)
  }
}
