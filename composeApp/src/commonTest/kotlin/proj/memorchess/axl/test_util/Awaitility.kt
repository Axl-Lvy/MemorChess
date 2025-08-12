package proj.memorchess.axl.test_util

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import proj.memorchess.axl.core.date.DateUtil

/**
 * Utility class for handling asynchronous waiting in tests.
 *
 * This class provides methods to wait for conditions to become true within a specified timeout.
 * It's particularly useful for UI tests where operations may not complete immediately.
 */
class Awaitility {

  companion object {

    /**
     * Waits until the specified condition becomes true or the timeout is reached.
     *
     * This method repeatedly checks the condition at regular intervals (100ms) until either:
     * - The condition returns true
     * - The timeout duration is exceeded, in which case an AssertionError is thrown
     *
     * Example usage:
     * ```
     * // Wait up to 5 seconds for a database operation to complete
     * Awaitility.awaitUntilTrue(5.seconds) {
     *   databaseOperation.isComplete()
     * }
     * ```
     *
     * @param timeout The maximum duration to wait for the condition to become true
     * @param condition A function that returns a boolean indicating if the condition is met
     * @throws AssertionError if the condition doesn't become true within the timeout period
     */
    fun awaitUntilTrue(
      timeout: Duration = TEST_TIMEOUT,
      failingMessage: String? = null,
      condition: () -> Boolean,
    ) {
      val startTime = DateUtil.now().toInstant(TimeZone.UTC)
      while (!condition()) {
        if (DateUtil.now().toInstant(TimeZone.UTC).minus(startTime) > timeout) {
          throw AssertionError(failingMessage ?: "Timed out waiting for condition")
        }
      }
    }
  }

  @Test
  fun testAwaitility() {
    // Example usage of Awaitility
    val startTime = DateUtil.now().toInstant(TimeZone.UTC)
    assertFailsWith<AssertionError> {
      awaitUntilTrue(1.seconds) {
        // Replace with your actual condition
        false // This should be a condition that eventually becomes true
      }
    }
    val endTime = DateUtil.now().toInstant(TimeZone.UTC)
    assertTrue { endTime.minus(startTime) > 1.seconds }
    assertTrue { endTime.minus(startTime) < 2.seconds }
  }
}
