package proj.memorchess.axl.ui.pages.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController

/** Abstracts app navigation. */
interface Navigator {

  /** Navigates to [route]. */
  fun navigateTo(route: Route)

  /** The current back stack entry as state. */
  @Composable fun currentBackStackEntryAsState(): State<NavBackStackEntry?>

  /** Runs [delegate] with the underlying [NavHostController]. */
  suspend fun callDelegate(delegate: suspend (NavHostController) -> Unit)
}

/**
 * Exposes the active [Navigator] to descendant composables. It is a composition local rather than a
 * Koin single because navigation state is composition scoped.
 */
val LocalNavigator =
  staticCompositionLocalOf<Navigator> { error("No Navigator provided in the composition tree") }
