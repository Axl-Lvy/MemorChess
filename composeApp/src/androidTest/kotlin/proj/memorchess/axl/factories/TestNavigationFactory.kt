package proj.memorchess.axl.factories

import proj.memorchess.axl.AUiTestFactory

class TestNavigationFactory : AUiTestFactory() {
  override fun createTests(): List<() -> Unit> {
    return listOf(::testGoToExplore, ::testGoToTraining, ::testGoToSettings)
  }

  override fun beforeEach() {
    // Nothing to do
  }

  fun testGoToExplore() {
    goToExplore()
  }

  fun testGoToTraining() {
    goToTraining()
  }

  fun testGoToSettings() {
    goToSettings()
  }
}
