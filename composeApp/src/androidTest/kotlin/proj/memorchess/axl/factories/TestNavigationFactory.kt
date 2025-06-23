package proj.memorchess.axl.factories

import proj.memorchess.axl.AUiTestFactory
import proj.memorchess.axl.util.UiTest

class TestNavigationFactory : AUiTestFactory() {

  override fun beforeEach() {
    // Nothing to do
  }

  @UiTest
  fun testGoToExplore() {
    goToExplore()
  }

  @UiTest
  fun testGoToTraining() {
    goToTraining()
  }

  @UiTest
  fun testGoToSettings() {
    goToSettings()
  }
}
