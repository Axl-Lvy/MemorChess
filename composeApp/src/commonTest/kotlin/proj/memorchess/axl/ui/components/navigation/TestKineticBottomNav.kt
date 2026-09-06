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
import proj.memorchess.axl.test_util.TestWithKoin
import proj.memorchess.axl.ui.pages.navigation.Route
import proj.memorchess.axl.ui.theme.KineticDarkPalette
import proj.memorchess.axl.ui.theme.KineticLightPalette

/** Per-cell test tag, mirroring the shape of the caller-supplied production tags. */
private fun tagOf(item: NavigationBarItemContent) = "nav_${item.name}"

/**
 * Pins [KineticBottomNav]'s selection contract: exactly one cell reports `selected`, the tagged
 * per-cell node stays clickable, and tapping the active cell is a no-op.
 *
 * Assertions go through test tags and semantics, never through label text: the resource locale is
 * not fixed on CI, so a text assertion would be flaky by construction.
 *
 * Koin is only strictly needed by [activeCellFollowsCurrentRoute], the one test that drives a live
 * active↔inactive transition and therefore reaches the reduce-motion setting behind
 * [proj.memorchess.axl.ui.theme.KineticMotion]; keeping the whole class on [TestWithKoin] is
 * simpler than splitting it.
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

  private fun ComposeUiTest.setBar(
    currentRoute: String,
    selected: MutableList<NavigationBarItemContent>,
  ) {
    setContent {
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
    setBar(Route.TrainingRoute.getLabel(), mutableListOf())

    items.forEach { onNodeWithTag(tagOf(it)).assertIsDisplayed() }
  }

  @Test
  fun onlyTheActiveCellIsSelected() = runTestFromSetup {
    setBar(Route.TrainingRoute.getLabel(), mutableListOf())

    onNodeWithTag(tagOf(NavigationBarItemContent.Training)).assertIsSelected()
    items
      .filter { it != NavigationBarItemContent.Training }
      .forEach { onNodeWithTag(tagOf(it)).assertIsNotSelected() }
  }

  @Test
  fun activeCellFollowsCurrentRoute() = runTestFromSetup {
    val route = mutableStateOf(Route.TrainingRoute.getLabel())
    setContent {
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
    waitForIdle()

    onNodeWithTag(tagOf(NavigationBarItemContent.Settings)).assertIsSelected()
    onNodeWithTag(tagOf(NavigationBarItemContent.Training)).assertIsNotSelected()
  }

  @Test
  fun tappingInactiveCellFiresOnSelect() = runTestFromSetup {
    val selected = mutableListOf<NavigationBarItemContent>()
    setBar(Route.TrainingRoute.getLabel(), selected)

    onNodeWithTag(tagOf(NavigationBarItemContent.Explore)).performClick()

    selected shouldBe listOf(NavigationBarItemContent.Explore)
  }

  @Test
  fun tappingActiveCellDoesNotFire() = runTestFromSetup {
    val selected = mutableListOf<NavigationBarItemContent>()
    setBar(Route.TrainingRoute.getLabel(), selected)

    onNodeWithTag(tagOf(NavigationBarItemContent.Training)).performClick()

    selected.shouldBeEmpty()
  }

  @Test
  fun activeCellIsStillClickable() = runTestFromSetup {
    setBar(Route.TrainingRoute.getLabel(), mutableListOf())

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
