package proj.memorchess.axl.util

import kotlin.time.Duration

/**
 * Utility class for handling asynchronous waiting in tests.
 *
 * This class provides methods to wait for conditions to become true within a specified timeout.
 * It's particularly useful for UI tests where operations may not complete immediately.
 */
object AwaitUtil {

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
  fun awaitUntilTrue(timeout: Duration, failingMessage: String? = null, condition: () -> Boolean) {
    val timeOutNano = timeout.inWholeNanoseconds
    val startTime = System.nanoTime()
    while (!condition()) {
      if (System.nanoTime() - startTime > timeOutNano) {
        throw AssertionError(failingMessage ?: "Timed out waiting for condition")
      }
      Thread.sleep(100)
    }
  }
}
