package proj.memorchess.axl.util

import proj.memorchess.axl.AUiTestFactory
import proj.memorchess.axl.factories.TestNavigationFactory
import proj.memorchess.axl.factories.TestSettingsFactory
import proj.memorchess.axl.factories.board.TestControlBarFactory
import proj.memorchess.axl.factories.board.TestNextMoveBarFactory
import proj.memorchess.axl.factories.board.TestSaveButtonFactory

/** Utility class for finding test factory classes. */
object FactoryClassFinder {

  /**
   * Returns a list of all test factories.
   *
   * @return A list of all test factory instances
   */
  fun getAllTestFactories(): List<AUiTestFactory> {
    return listOf(
      TestSettingsFactory(),
      TestNavigationFactory(),
      TestControlBarFactory(),
      TestNextMoveBarFactory(),
      TestSaveButtonFactory(),
    )
  }
}
