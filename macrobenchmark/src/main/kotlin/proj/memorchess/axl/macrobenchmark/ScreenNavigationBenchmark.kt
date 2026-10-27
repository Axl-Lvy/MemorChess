package proj.memorchess.axl.macrobenchmark

import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Measures frame timing while cycling through the three main screens via the bottom navigation bar.
 * Startup runs in the setup block, outside the measured window.
 */
@RunWith(AndroidJUnit4::class)
class ScreenNavigationBenchmark {

  @get:Rule val benchmarkRule = MacrobenchmarkRule()

  @Test
  fun screenTransitions() {
    benchmarkRule.measureRepeated(
      packageName = TARGET_PACKAGE,
      metrics = listOf(FrameTimingMetric()),
      iterations = 10,
      startupMode = StartupMode.WARM,
      setupBlock = { startActivityAndWait() },
    ) {
      navigateTo("Today")
      navigateTo("Settings")
      navigateTo("Explore")
    }
  }
}
