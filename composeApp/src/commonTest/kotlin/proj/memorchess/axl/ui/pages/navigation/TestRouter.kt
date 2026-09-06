package proj.memorchess.axl.ui.pages.navigation

import io.kotest.matchers.shouldBe
import kotlin.test.Test

/**
 * Coverage for [isTabToTabTransition], the route string classifier that Router.kt's transition
 * selection reads.
 */
internal class TestRouter {

  @Test
  fun `two bottom-nav tabs are a tab-to-tab transition`() {
    isTabToTabTransition("training", "library") shouldBe true
    isTabToTabTransition("explore?position=abc&repertoireId=xyz", "settings") shouldBe true
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
    isTabToTabTransition("", "training") shouldBe false
    isTabToTabTransition("training", "") shouldBe false
  }
}
