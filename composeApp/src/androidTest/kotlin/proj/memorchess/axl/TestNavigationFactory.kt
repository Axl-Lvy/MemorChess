package proj.memorchess.axl

import proj.memorchess.axl.ui.pages.navigation.Destination

class TestNavigationFactory : AUiTestFactory() {
  override fun createTests(): List<() -> Unit> {
    return listOf(::testGoToExplore, ::testGoToTraining, ::testGoToSettings)
  }

  fun testGoToExplore() {
    clickOnExplore()
    assertNodeWithTagExists(Destination.EXPLORE.name)
  }

  fun testGoToTraining() {
    clickOnTraining()
    assertNodeWithTagExists(Destination.TRAINING.name)
  }

  fun testGoToSettings() {
    clickOnSettings()
    assertNodeWithTagExists(Destination.SETTINGS.name)
  }
}
