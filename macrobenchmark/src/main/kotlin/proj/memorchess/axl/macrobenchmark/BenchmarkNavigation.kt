package proj.memorchess.axl.macrobenchmark

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until

/** Application id of the app under benchmark. */
internal const val TARGET_PACKAGE = "proj.memorchess.axl"

private const val UI_TIMEOUT_MS = 10_000L

/**
 * Taps the bottom navigation item for [label] and waits for the UI to settle. Needs
 * `testTagsAsResourceId` in `MainActivity` so UiAutomator can find the test tags.
 */
internal fun MacrobenchmarkScope.navigateTo(label: String) {
  val tag = "bottom_navigation_bar_item_$label"
  check(device.wait(Until.hasObject(By.res(tag)), UI_TIMEOUT_MS)) {
    "Bottom navigation item '$tag' not found on screen"
  }
  device.findObject(By.res(tag)).click()
  device.waitForIdle()
}
