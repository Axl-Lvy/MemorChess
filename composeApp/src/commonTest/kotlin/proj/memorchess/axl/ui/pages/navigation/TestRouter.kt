package proj.memorchess.axl.ui.pages.navigation

import io.kotest.matchers.shouldBe
import kotlin.test.Test
import proj.memorchess.axl.ui.components.navigation.NavigationBarItemContent

/**
 * Coverage for [isTabToTabTransition], the route string classifier that Router.kt's transition
 * selection reads.
 */
internal class TestRouter {

  @Test
  fun `two bottom-nav tabs are a tab-to-tab transition`() {
    // "today", not "training": the Training tab's destination is now Route.TodayRoute.
    isTabToTabTransition("today", "library") shouldBe true
    isTabToTabTransition("explore?position=abc&repertoireId=xyz", "settings") shouldBe true
  }

  @Test
  fun `every pair of bottom-nav destinations from the registry is a tab-to-tab transition`() {
    // Derived from NavigationBarItemContent itself, rather than literal route strings, so a
    // destination added to that registry (e.g. a future Today tab) is covered here for free.
    val labels = NavigationBarItemContent.entries.map { it.destination.getLabel().lowercase() }
    for (from in labels) {
      for (to in labels) {
        isTabToTabTransition(from, to) shouldBe true
      }
    }
  }

  @Test
  fun `a registry destination against the repertoire viewer is never a tab-to-tab transition`() {
    NavigationBarItemContent.entries.forEach { item ->
      val label = item.destination.getLabel().lowercase()
      isTabToTabTransition(label, "repertoireview/italian-game") shouldBe false
      isTabToTabTransition("repertoireview/italian-game", label) shouldBe false
    }
  }

  @Test
  fun `a push into the repertoire viewer is not a tab-to-tab transition`() {
    isTabToTabTransition("library", "repertoireview/italian-game") shouldBe false
  }

  @Test
  fun `a pop back out of the repertoire viewer is not a tab-to-tab transition either`() {
    isTabToTabTransition("repertoireview/italian-game", "library") shouldBe false
  }

  @Test
  fun `an unrecognised or empty route is never a tab-to-tab transition`() {
    isTabToTabTransition("", "today") shouldBe false
    isTabToTabTransition("today", "") shouldBe false
  }
}
