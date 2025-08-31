package proj.memorchess.axl.test_util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import proj.memorchess.axl.ui.pages.navigation.Navigator
import proj.memorchess.axl.ui.pages.navigation.Route

/** A no-operation implementation of the Navigator interface for testing purposes */
open class NoOpNavigator : Navigator {
  override fun navigateTo(route: Route) {
    // Nothing to do
  }

  @Composable
  override fun currentBackStackEntryAsState(): State<NavBackStackEntry?> {
    return derivedStateOf { null }
  }

  override suspend fun callDelegate(delegate: suspend (NavHostController) -> Unit) {
    // Nothing to do
  }
}
