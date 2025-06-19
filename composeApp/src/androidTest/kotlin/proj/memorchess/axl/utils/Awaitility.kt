package proj.memorchess.axl.utils

import kotlin.time.Duration

object Awaitility {

  fun awaitUntilTrue(timeout: Duration, condition: () -> Boolean) {
    val timeOutNano = timeout.inWholeNanoseconds
    val startTime = System.nanoTime()
    while (!condition()) {
      if (System.nanoTime() - startTime > timeOutNano) {
        throw AssertionError("Timed out waiting for condition")
      }
      Thread.sleep(100)
    }
  }
}
