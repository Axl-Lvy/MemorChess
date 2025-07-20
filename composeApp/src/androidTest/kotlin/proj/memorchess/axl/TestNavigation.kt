package proj.memorchess.axl

import kotlin.test.Test
import proj.memorchess.axl.utils.UiTestFromMainActivity

class TestNavigation : UiTestFromMainActivity() {

  @Test
  fun testGoToExplore() {
    goToExplore()
  }

  @Test
  fun testGoToTraining() {
    goToTraining()
  }

  @Test
  fun testGoToSettings() {
    goToSettings()
  }
}
