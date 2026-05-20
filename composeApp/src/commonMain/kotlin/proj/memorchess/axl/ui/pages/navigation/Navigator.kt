package proj.memorchess.axl.ui.pages.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController

/** Navigator interface to abstract navigation logic. */
interface Navigator {

  /** Navigate to a specific route. */
  fun navigateTo(route: Route)

  /** Get the current back stack entry as a state. */
  @Composable fun currentBackStackEntryAsState(): State<NavBackStackEntry?>

  /** Call a delegate function with the underlying NavHostController. */
  suspend fun callDelegate(delegate: suspend (NavHostController) -> Unit)
}

/**
 * Composition local that exposes the active [Navigator] to descendant composables.
 *
 * Provided once at the root by [proj.memorchess.axl.ui.App] (for production) or by the test
 * harness. Components that need to read or trigger navigation consume this rather than going
 * through the DI container, which keeps composition scoped state out of the process scoped Koin
 * container.
 */
val LocalNavigator =
  staticCompositionLocalOf<Navigator> { error("No Navigator provided in the composition tree") }
