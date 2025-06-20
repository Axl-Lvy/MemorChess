package proj.memorchess.axl

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import com.diamondedge.logging.logging
import kotlin.test.BeforeTest
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.fail
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import proj.memorchess.axl.core.config.IAppConfig
import proj.memorchess.axl.core.data.DatabaseHolder
import proj.memorchess.axl.core.data.StoredNode
import proj.memorchess.axl.core.graph.nodes.NodeManager
import proj.memorchess.axl.factories.TestNavigationFactory
import proj.memorchess.axl.factories.TestSettingsFactory
import proj.memorchess.axl.factories.board.TestControlBarFactory
import proj.memorchess.axl.factories.board.TestNextMoveBarFactory
import proj.memorchess.axl.factories.board.TestSaveButton
import proj.memorchess.axl.game.getScandinavian
import proj.memorchess.axl.game.getVienna
import proj.memorchess.axl.test_util.TEST_TIMEOUT
import proj.memorchess.axl.test_util.TestConfig
import proj.memorchess.axl.test_util.TestDatabase
import proj.memorchess.axl.test_util.getNavigationButtonDescription
import proj.memorchess.axl.ui.pages.navigation.Destination
import proj.memorchess.axl.ui.util.DateUtil
import proj.memorchess.axl.utils.Awaitility

@OptIn(ExperimentalTestApi::class)
class TestRunner {
  @get:Rule val composeTestRule = createAndroidComposeRule<MainActivity>()

  private val testFactories =
    listOf(
      TestNavigationFactory(),
      TestSettingsFactory(),
      TestControlBarFactory(),
      TestNextMoveBarFactory(),
      TestSaveButton(),
    )

  @BeforeTest
  fun setUp() {
    composeTestRule.mainClock.autoAdvance = true
    IAppConfig.set(TestConfig)
  }

  @Test
  fun runTests() {
    val failedTests = mutableMapOf<String, Int>()
    val successTests = mutableMapOf<String, Int>()
    for (testFactory in testFactories) {
      testFactory.composeTestRule = composeTestRule
      for (test in testFactory.createTests()) {
        reset(testFactory.needsDatabaseReset())
        try {
          testFactory.beforeEach()
          test()
          successTests.compute(testFactory.javaClass.name) { _, value ->
            if (value == null) 1 else value + 1
          }
        } catch (t: Throwable) {
          failedTests.compute(testFactory.javaClass.name) { _, value ->
            if (value == null) 1 else value + 1
          }
          throw t
        }
      }
    }
    if (successTests.isNotEmpty()) {
      val message = StringBuilder()
      for (failedTestClass in failedTests) {
        message.append(
          "${failedTestClass.value} tests run with success in ${failedTestClass.key}\n"
        )
      }
      LOGGER.info { "${successTests.values.sum()} tests run with success : \n$message" }
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
    val testFactory = TestSaveButton()
    testFactory.composeTestRule = composeTestRule
    for (test in testFactory.createTests()) {
      reset(testFactory.needsDatabaseReset())
      testFactory.beforeEach()
      test()
    }
    // Always fail so that we are sure this test is ignored
    fail("SUCCESS")
  }

  @Test
  @Ignore("Use this to run a single test")
  fun runSingleTest() {
    val testFactory = TestSaveButton()
    testFactory.composeTestRule = composeTestRule
    reset(testFactory.needsDatabaseReset())
    testFactory.beforeEach()
    testFactory.testPropagateSave()
    // Always fail so that we are sure this test is ignored
    fail("SUCCESS")
  }

  private fun reset(resetDatabase: Boolean) {
    composeTestRule.mainClock.autoAdvance = true
    val trainingDescription = getNavigationButtonDescription(Destination.TRAINING.name)
    composeTestRule.waitUntilAtLeastOneExists(hasContentDescription(trainingDescription))
    composeTestRule.onNodeWithContentDescription(trainingDescription).assertExists().performClick()

    runTest {
      if (resetDatabase) {
        resetDatabase()
      }
      NodeManager.resetCacheFromDataBase()
    }
  }

  private suspend fun resetDatabase() {
    DatabaseHolder.getDatabase().deleteAllNodes()
    DatabaseHolder.getDatabase().deleteAllMoves()
    Awaitility.awaitUntilTrue(TEST_TIMEOUT) {
      lateinit var allPositions: List<StoredNode>
      runTest { allPositions = DatabaseHolder.getDatabase().getAllPositions() }
      allPositions.isEmpty()
    }
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
    val storedNodes = (viennaNodes + scandinavianNodes)
    for (node in storedNodes) {
      DatabaseHolder.getDatabase().insertPosition(node)
    }
    Awaitility.awaitUntilTrue(TEST_TIMEOUT) {
      lateinit var allPositions: List<StoredNode>
      runBlocking { allPositions = DatabaseHolder.getDatabase().getAllPositions() }
      allPositions.size == storedNodes.map { it.positionKey }.distinct().size
    }
  }
}

private val LOGGER = logging()
