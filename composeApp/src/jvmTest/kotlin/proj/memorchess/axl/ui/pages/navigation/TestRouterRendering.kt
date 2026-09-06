package proj.memorchess.axl.ui.pages.navigation

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import kotlin.test.Test
import proj.memorchess.axl.test_util.TestWithKoin
import proj.memorchess.axl.ui.assertNodeWithTagExists
import proj.memorchess.axl.ui.pages.REPERTOIRE_VIEW_TEST_TAG
import proj.memorchess.axl.ui.theme.AppTheme

/**
 * Smoke coverage for [Router] itself, rather than for [isTabToTabTransition] in isolation. Renders
 * the real `NavHost` and drives it through tab to tab navigation and a push and pop onto
 * [Route.RepertoireViewRoute]. That is otherwise only reachable from the device only androidApp
 * instrumented tests. Provides a real [DelegateNavigator] rather than [TestWithKoin]'s recording
 * fake, since the point here is to actually navigate the controller [Router] renders.
 */
@OptIn(ExperimentalTestApi::class)
internal class TestRouterRendering : TestWithKoin() {

  private fun runTestFromSetup(block: suspend ComposeUiTest.(NavHostController) -> Unit) =
    runComposeUiTest {
      koinSetUp()
      try {
        var navController: NavHostController? = null
        setContent {
          val controller = rememberNavController()
          navController = controller
          CompositionLocalProvider(LocalNavigator provides DelegateNavigator(controller)) {
            AppTheme { Router(controller) }
          }
        }
        waitForIdle()
        block(checkNotNull(navController))
      } finally {
        koinTearDown()
      }
    }

  @Test
  fun rendersTheStartDestinationWithNoCrash() = runTestFromSetup {
    // The start destination is Route.TodayRoute, not the board directly; see Route.TodayRoute.
    assertNodeWithTagExists(Route.TodayRoute.getLabel())
  }

  @Test
  fun pushingTheTrainingBoardFromTodayRendersWithNoCrash() = runTestFromSetup { navController ->
    navController.navigate(Route.TrainingRoute.DEFAULT)
    waitForIdle()
    assertNodeWithTagExists(Route.TrainingRoute.DEFAULT.getLabel())

    navController.popBackStack()
    waitForIdle()
    assertNodeWithTagExists(Route.TodayRoute.getLabel())
  }

  @Test
  fun navigatingTabToTabRendersEachDestinationWithNoCrash() = runTestFromSetup { navController ->
    navController.navigate(Route.LibraryRoute)
    waitForIdle()
    assertNodeWithTagExists(Route.LibraryRoute.getLabel())

    navController.navigate(Route.SettingsRoute)
    waitForIdle()
    assertNodeWithTagExists(Route.SettingsRoute.getLabel())

    navController.navigate(Route.ExploreRoute.DEFAULT)
    waitForIdle()
    assertNodeWithTagExists(Route.ExploreRoute.DEFAULT.getLabel())
  }

  @Test
  fun pushingAndPoppingTheRepertoireViewerRendersWithNoCrash() = runTestFromSetup { navController ->
    navController.navigate(Route.LibraryRoute)
    waitForIdle()

    navController.navigate(Route.RepertoireViewRoute(repertoireId = "does-not-exist"))
    waitForIdle()
    assertNodeWithTagExists(REPERTOIRE_VIEW_TEST_TAG)

    navController.popBackStack()
    waitForIdle()
    assertNodeWithTagExists(Route.LibraryRoute.getLabel())
  }
}
