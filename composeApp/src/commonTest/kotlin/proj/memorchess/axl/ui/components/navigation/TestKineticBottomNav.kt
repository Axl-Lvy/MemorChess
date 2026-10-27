package proj.memorchess.axl.ui.components.navigation

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.font.FontWeight
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlin.test.Test
import proj.memorchess.axl.core.config.REDUCE_MOTION_SETTING
import proj.memorchess.axl.test_util.TestWithKoin
import proj.memorchess.axl.ui.pages.navigation.Route
import proj.memorchess.axl.ui.setTestContent
import proj.memorchess.axl.ui.theme.KineticDarkPalette
import proj.memorchess.axl.ui.theme.KineticLightPalette

private fun tagOf(item: NavigationBarItemContent) = "nav_${item.name}"

/**
 * Pins [KineticBottomNav]'s selection contract. Asserts through tags and semantics, never label
 * text, since the CI locale is not fixed.
 */
@OptIn(ExperimentalTestApi::class)
internal class TestKineticBottomNav : TestWithKoin() {

  private val items = NavigationBarItemContent.entries.sortedBy { it.index }

  private fun runTestFromSetup(block: suspend ComposeUiTest.() -> Unit) = runComposeUiTest {
    koinSetUp()
    try {
      block()
    } finally {
      koinTearDown()
    }
  }

  private suspend fun ComposeUiTest.setBar(
    currentRoute: String,
    selected: MutableList<NavigationBarItemContent>,
  ) {
    setTestContent {
      InitializeApp {
        KineticBottomNav(
          items = items,
          currentRoute = currentRoute,
          onSelect = { selected += it },
          itemModifier = { Modifier.testTag(tagOf(it)) },
        )
      }
    }
  }

  @Test
  fun everyItemRendersACell() = runTestFromSetup {
    setBar(NavigationBarItemContent.Training.destination.getLabel(), mutableListOf())

    items.forEach { onNodeWithTag(tagOf(it)).assertIsDisplayed() }
  }

  @Test
  fun onlyTheActiveCellIsSelected() = runTestFromSetup {
    setBar(NavigationBarItemContent.Training.destination.getLabel(), mutableListOf())

    onNodeWithTag(tagOf(NavigationBarItemContent.Training)).assertIsSelected()
    items
      .filter { it != NavigationBarItemContent.Training }
      .forEach { onNodeWithTag(tagOf(it)).assertIsNotSelected() }
  }

  @Test
  fun trainingCellStaysSelectedOnThePushedTrainingBoard() = runTestFromSetup {
    // Training's destination is TodayRoute, but its tab must stay selected on the pushed board too.
    setBar(Route.TrainingRoute.DEFAULT.getLabel(), mutableListOf())

    onNodeWithTag(tagOf(NavigationBarItemContent.Training)).assertIsSelected()
    items
      .filter { it != NavigationBarItemContent.Training }
      .forEach { onNodeWithTag(tagOf(it)).assertIsNotSelected() }
  }

  @Test
  fun activeCellFollowsCurrentRoute() = runTestFromSetup {
    val route = mutableStateOf(NavigationBarItemContent.Training.destination.getLabel())
    setTestContent {
      InitializeApp {
        KineticBottomNav(
          items = items,
          currentRoute = route.value,
          onSelect = {},
          itemModifier = { Modifier.testTag(tagOf(it)) },
        )
      }
    }
    onNodeWithTag(tagOf(NavigationBarItemContent.Training)).assertIsSelected()

    route.value = Route.SettingsRoute.getLabel()
    awaitIdle()

    onNodeWithTag(tagOf(NavigationBarItemContent.Settings)).assertIsSelected()
    onNodeWithTag(tagOf(NavigationBarItemContent.Training)).assertIsNotSelected()
  }

  @Test
  fun selectionStillFollowsRouteWhenReduceMotionIsOn() = runTestFromSetup {
    // The icon pop is invisible to semantics; TestKineticBottomNavReduceMotion checks pixels.
    REDUCE_MOTION_SETTING.setValue(true)
    val route = mutableStateOf(Route.ExploreRoute.DEFAULT.getLabel())
    setTestContent {
      InitializeApp {
        KineticBottomNav(
          items = items,
          currentRoute = route.value,
          onSelect = {},
          itemModifier = { Modifier.testTag(tagOf(it)) },
        )
      }
    }

    route.value = Route.TrainingRoute.DEFAULT.getLabel()
    awaitIdle()

    onNodeWithTag(tagOf(NavigationBarItemContent.Training)).assertIsSelected()
    onNodeWithTag(tagOf(NavigationBarItemContent.Explore)).assertIsNotSelected()
  }

  @Test
  fun eachItemIsTheOnlySelectedCellForItsOwnRoute() = runTestFromSetup {
    // isActive() matches each item on a case-insensitive label, so every item's route is driven.
    val route = mutableStateOf(items.first().destination.getLabel())
    setTestContent {
      InitializeApp {
        KineticBottomNav(
          items = items,
          currentRoute = route.value,
          onSelect = {},
          itemModifier = { Modifier.testTag(tagOf(it)) },
        )
      }
    }

    items.forEach { active ->
      route.value = active.destination.getLabel()
      awaitIdle()

      onNodeWithTag(tagOf(active)).assertIsSelected()
      items.filter { it != active }.forEach { onNodeWithTag(tagOf(it)).assertIsNotSelected() }
    }
  }

  @Test
  fun tappingInactiveCellFiresOnSelect() = runTestFromSetup {
    val selected = mutableListOf<NavigationBarItemContent>()
    setBar(NavigationBarItemContent.Training.destination.getLabel(), selected)

    onNodeWithTag(tagOf(NavigationBarItemContent.Explore)).performClick()

    selected shouldBe listOf(NavigationBarItemContent.Explore)
  }

  @Test
  fun tappingActiveCellDoesNotFire() = runTestFromSetup {
    val selected = mutableListOf<NavigationBarItemContent>()
    setBar(NavigationBarItemContent.Training.destination.getLabel(), selected)

    onNodeWithTag(tagOf(NavigationBarItemContent.Training)).performClick()

    selected.shouldBeEmpty()
  }

  @Test
  fun activeCellIsStillClickable() = runTestFromSetup {
    setBar(NavigationBarItemContent.Training.destination.getLabel(), mutableListOf())

    onNodeWithTag(tagOf(NavigationBarItemContent.Training)).assertHasClickAction()
  }

  @Test
  fun everyCellIsRenderedForAnEmptyRoute() = runTestFromSetup {
    setBar("", mutableListOf())

    items.forEach { onNodeWithTag(tagOf(it)).assertIsNotSelected() }
  }

  @Test
  fun activeStyleUsesActionDimAndAction() {
    kineticNavCellStyle(KineticLightPalette, active = true) shouldBe
      KineticNavCellStyle(
        KineticLightPalette.actionDim,
        KineticLightPalette.action,
        FontWeight.Black,
      )
    kineticNavCellStyle(KineticDarkPalette, active = true) shouldBe
      KineticNavCellStyle(KineticDarkPalette.actionDim, KineticDarkPalette.action, FontWeight.Black)
  }

  @Test
  fun inactiveStyleIsActionDimAtZeroAlpha() {
    listOf(KineticLightPalette, KineticDarkPalette).forEach { palette ->
      val style = kineticNavCellStyle(palette, active = false)
      style.pill.alpha shouldBe 0f
      style.pill shouldBe palette.actionDim.copy(alpha = 0f)
      // Color.Transparent is black at alpha 0; fading the violet pill to it would drag the
      // mid-animation frames through grey.
      style.pill shouldNotBe Color.Transparent
      style.content shouldBe palette.ink3
      style.labelWeight shouldBe FontWeight.ExtraBold
    }
  }
}
