package proj.memorchess.axl.ui.pages.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController

/** Navigator interface to abstract navigation logic */
interface Navigator {

  /** Navigate to a specific route */
  fun navigateTo(route: Route)

  /** Get the current back stack entry as a state */
  @Composable
  fun currentBackStackEntryAsState(): State<NavBackStackEntry?>

  /** Call a delegate function with the NavHostController */
  suspend fun callDelegate(delegate: suspend (NavHostController) -> Unit)
}
