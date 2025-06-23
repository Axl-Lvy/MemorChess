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
import proj.memorchess.axl.core.data.StoredMove
import proj.memorchess.axl.core.data.StoredNode
import proj.memorchess.axl.core.graph.nodes.NodeManager
import proj.memorchess.axl.factories.board.TestNextMoveBarFactory
import proj.memorchess.axl.factories.board.TestSaveButtonFactory
import proj.memorchess.axl.game.getScandinavian
import proj.memorchess.axl.game.getVienna
import proj.memorchess.axl.test_util.TEST_TIMEOUT
import proj.memorchess.axl.test_util.TestConfig
import proj.memorchess.axl.test_util.TestDatabase
import proj.memorchess.axl.test_util.getNavigationButtonDescription
import proj.memorchess.axl.ui.pages.navigation.Destination
import proj.memorchess.axl.ui.util.DateUtil
import proj.memorchess.axl.util.AwaitUtil
import proj.memorchess.axl.util.FactoryClassFinder

/**
 * Main test runner for the application.
 *
 * This class is responsible for executing all [factory-based][AUiTestFactory] UI tests in the
 * application. It uses a factory pattern to organize tests into logical groups and provides
 * utilities for test setup, execution, and reporting.
 *
 * Key features:
 * - Runs all tests in a single application instance for improved performance
 * - Manages test lifecycle (setup, execution, teardown)
 * - Provides utilities for database reset and initialization
 * - Reports test results (success/failure)
 *
 * To add new tests:
 * 1. Create a new class extending [AUiTestFactory]
 * 2. Implement the required methods
 * 3. Annotate the tests with [UiTest][proj.memorchess.axl.util.UiTest]
 * 4. Add the new factory to the [FactoryClassFinder.getAllTestFactories] list
 */
@OptIn(ExperimentalTestApi::class)
class TestRunner {
  /**
   * The Compose test rule used to interact with the UI. This is automatically initialized by JUnit
   * before tests are executed.
   */
  @get:Rule val composeTestRule = createAndroidComposeRule<MainActivity>()

  /**
   * List of test factories to be executed by the test runner.
   *
   * Each factory contains a group of related tests that test a specific feature or component. To
   * add new tests, create a new factory and add it to this list.
   *
   * This list is automatically populated with all classes extending AUiTestFactory using
   * reflection.
   */
  private val testFactories: List<AUiTestFactory> by lazy {
    // Get all test factories using the ClassFinder utility
    FactoryClassFinder.getAllTestFactories()
  }

  /**
   * Sets up the test environment before each test.
   *
   * This method is called by JUnit before each test method is executed. It configures the test
   * environment with the appropriate settings.
   */
  @BeforeTest
  fun setUp() {
    composeTestRule.mainClock.autoAdvance = true
    IAppConfig.set(TestConfig)
    DatabaseHolder.init(TestDatabase.empty())
    reset(true)
  }

  /**
   * Runs all tests from all registered test factories.
   *
   * This is the main entry point for running tests. It:
   * 1. Iterates through all test factories
   * 2. Executes each test in the factory
   * 3. Tracks test success and failure
   * 4. Reports test results
   *
   * If any test fails, this method will throw an exception with details about the failures.
   */
  @Test
  fun runTests() {
    LOGGER.error { testFactories.toString() }
    val failedTests = mutableMapOf<String, Int>()
    val successTests = mutableMapOf<String, Int>()
    var exception: Throwable? = null
    for (testFactory in testFactories) {
      var firstTest = true
      testFactory.composeTestRule = composeTestRule
      for (test in testFactory.createTests()) {
        try {
          reset(firstTest || testFactory.needsDatabaseReset())
          firstTest = false
          testFactory.beforeEach()
          test()
          successTests.compute(testFactory.javaClass.name) { _, value ->
            if (value == null) 1 else value + 1
          }
        } catch (t: Throwable) {
          failedTests.compute(testFactory.javaClass.name) { _, value ->
            if (value == null) 1 else value + 1
          }
          if (exception == null) {
            exception = t
          }
        }
      }
    }
    if (successTests.isNotEmpty()) {
      val message = StringBuilder()
      for (successTest in successTests) {
        message.append("${successTest.value} tests run with success in ${successTest.key}\n")
      }
      LOGGER.info { "${successTests.values.sum()} tests run with success : \n$message" }
    }
    if (failedTests.isNotEmpty()) {
      val message = StringBuilder()
      for (failedTestClass in failedTests) {
        message.append("${failedTestClass.value} tests failed in ${failedTestClass.key}\n")
      }
      fail(
        "${failedTests.values.sum()} tests failed : $message with exception ${exception!!.stackTraceToString()} $failedTests"
      )
    }
  }

  /**
   * Runs all tests from a single test factory.
   *
   * This method is useful for debugging or focusing on a specific group of tests. To use it, modify
   * the code to use your test factory of interest.
   *
   * Note: This test is ignored by default to prevent it from running during normal test execution.
   */
  @Test
//  @Ignore("Use this to run a single test class")
  fun runSingleTestClass() {
    val testFactory = TestSaveButtonFactory()
    testFactory.composeTestRule = composeTestRule
    for (test in testFactory.createTests()) {
      reset(testFactory.needsDatabaseReset())
      testFactory.beforeEach()
      test()
    }
    // Always fail so that we are sure this test is ignored
    fail("SUCCESS")
  }

  /**
   * Runs a single test from a test factory.
   *
   * This method is useful for debugging or focusing on a specific test. To use it, modify the code
   * to use your test factory and test method of interest.
   *
   * Note: This test is ignored by default to prevent it from running during normal test execution.
   */
  @Test
//  @Ignore("Use this to run a single test")
  fun runSingleTest() {
    val testFactory = TestNextMoveBarFactory()
    reset(testFactory.needsDatabaseReset())
    testFactory.composeTestRule = composeTestRule
    testFactory.beforeEach()
    testFactory.testMultipleNextMoves()
    // Always fail so that we are sure this test is ignored
    fail("SUCCESS")
  }

  /**
   * Resets the application state between tests.
   *
   * This method:
   * 1. Ensures the UI is in a responsive state
   * 2. Navigates to the Training screen
   * 3. Optionally resets the database if requested
   * 4. Resets the node manager cache
   *
   * This approach allows tests to run in isolation without requiring a full app restart,
   * significantly improving test execution speed.
   *
   * @param resetDatabase Whether to reset the database to its initial state
   */
  private fun reset(resetDatabase: Boolean) {
    val trainingDescription = getNavigationButtonDescription(Destination.TRAINING.name)
    composeTestRule.waitUntilAtLeastOneExists(hasContentDescription(trainingDescription))
    runTest {
      if (resetDatabase) {
        resetDatabase()
      }
      NodeManager.resetCacheFromDataBase()
    }
    composeTestRule.onNodeWithContentDescription(trainingDescription).assertExists().performClick()
  }

  /**
   * Resets the database to a known initial state with test data.
   *
   * This method:
   * 1. Deletes all existing nodes and moves from the database
   * 2. Waits for the database to be empty
   * 3. Populates the database with test data (Vienna and Scandinavian openings)
   * 4. Waits for the database to be populated
   *
   * The test data includes:
   * - Vienna opening positions (with yesterday/today dates)
   * - Scandinavian opening positions (with older dates)
   *
   * This provides a consistent starting point for tests that require database access.
   */
  private suspend fun resetDatabase() {
    AwaitUtil.awaitUntilTrue(TEST_TIMEOUT, failingMessage = "Database not empty") {
      runTest {
        DatabaseHolder.getDatabase().deleteAllNodes()
        DatabaseHolder.getDatabase().deleteAllMoves()
      }
      lateinit var allPositions: List<StoredNode>
      lateinit var allMoves: List<StoredMove>
      runTest {
        allPositions = DatabaseHolder.getDatabase().getAllPositions()
        allMoves = DatabaseHolder.getDatabase().getAllMoves()
      }
      allPositions.isEmpty() && allMoves.isEmpty()
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
    AwaitUtil.awaitUntilTrue(TEST_TIMEOUT, failingMessage = "Database not populated") {
      lateinit var allPositions: List<StoredNode>
      runBlocking { allPositions = DatabaseHolder.getDatabase().getAllPositions() }
      allPositions.size == storedNodes.map { it.positionKey }.distinct().size
    }
  }
}

/**
 * Logger instance for the TestRunner class. Used to log test execution results and other diagnostic
 * information.
 */
private val LOGGER = logging()
