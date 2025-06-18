package proj.memorchess.axl

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import kotlin.test.BeforeTest
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.fail
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import proj.memorchess.axl.core.config.IAppConfig
import proj.memorchess.axl.core.data.DatabaseHolder
import proj.memorchess.axl.core.data.StoredNode
import proj.memorchess.axl.core.graph.nodes.NodeManager
import proj.memorchess.axl.game.getScandinavian
import proj.memorchess.axl.game.getVienna
import proj.memorchess.axl.test_util.TestConfig
import proj.memorchess.axl.test_util.TestDatabase
import proj.memorchess.axl.test_util.getNavigationButtonDescription
import proj.memorchess.axl.ui.pages.navigation.Destination
import proj.memorchess.axl.ui.util.DateUtil

@OptIn(ExperimentalTestApi::class)
class TestRunner {
  @get:Rule val composeTestRule = createAndroidComposeRule<MainActivity>()

  private val testFactories = listOf(TestNavigationFactory(), TestExploreFactory())

  @BeforeTest
  fun setUp() {
    composeTestRule.mainClock.autoAdvance = true
    IAppConfig.set(TestConfig)
  }

  @Test
  fun runTests() {
    val failedTests = mutableMapOf<String, Int>()
    for (testFactory in testFactories) {
      testFactory.composeTestRule = composeTestRule
      for (test in testFactory.createTests()) {
        reset()
        try {
          test()
        } catch (t: Throwable) {
          failedTests.compute(testFactory.javaClass.simpleName) { _, value ->
            if (value == null) 1 else value + 1
          }
        }
      }
    }
    if (failedTests.isNotEmpty()) {
      val message = StringBuilder()
      for (failedTestClass in failedTests) {
        message.append("${failedTestClass.value} tests failed in ${failedTestClass.key}\n")
      }
      fail("${failedTests.values.sum()} tests failed : \n$message")
    }
  }

  @Test
  @Ignore("Use this to run a single test class")
  fun runSingleTestClass() {
    val testFactory = TestNavigationFactory()
    testFactory.composeTestRule = composeTestRule
    for (test in testFactory.createTests()) {
      reset()
      test()
    }
  }

  @Test
  @Ignore("Use this to run a single test")
  fun runSingleTest() {
    val testFactory = TestNavigationFactory()
    testFactory.composeTestRule = composeTestRule
    testFactory.testGoToExplore()
  }

  private fun reset() {
    composeTestRule.mainClock.autoAdvance = true
    val trainingDescription = getNavigationButtonDescription(Destination.TRAINING.name)
    composeTestRule.waitUntilAtLeastOneExists(hasContentDescription(trainingDescription))
    composeTestRule.onNodeWithContentDescription(trainingDescription).assertExists().performClick()

    runTest {
      resetDatabase()
      NodeManager.resetCacheFromDataBase()
    }
  }

  private suspend fun resetDatabase() {
    DatabaseHolder.getDatabase().deleteAllNodes()
    val viennaNodes =
      TestDatabase.convertStringMovesToNodes(getVienna()).map {
        StoredNode(
          it.positionKey,
          it.previousMoves,
          it.nextMoves,
          DateUtil.yesterday(),
          DateUtil.today(),
        )
      }
    val scandinavianNodes =
      TestDatabase.convertStringMovesToNodes(getScandinavian()).map {
        StoredNode(
          it.positionKey,
          it.previousMoves,
          it.nextMoves,
          DateUtil.dateInDays(-2),
          DateUtil.tomorrow(),
        )
      }
    for (node in viennaNodes + scandinavianNodes) {
      DatabaseHolder.getDatabase().insertPosition(node)
    }
  }
}
