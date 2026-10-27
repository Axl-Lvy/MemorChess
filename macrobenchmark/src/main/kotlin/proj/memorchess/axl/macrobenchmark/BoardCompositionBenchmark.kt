package proj.memorchess.axl.macrobenchmark

import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.TraceSectionMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Measures how long composing the chess board takes when entering the Explore screen. No frame
 * timing metric: composition tracing skews it, and CI emulators often emit no frame slices.
 */
@OptIn(ExperimentalMetricApi::class)
@RunWith(AndroidJUnit4::class)
class BoardCompositionBenchmark {

  @get:Rule val benchmarkRule = MacrobenchmarkRule()

  @Test
  fun boardCompositionOnExploreEntry() {
    benchmarkRule.measureRepeated(
      packageName = TARGET_PACKAGE,
      metrics = listOf(TraceSectionMetric("%BoardGrid%", TraceSectionMetric.Mode.Sum)),
      iterations = 10,
      startupMode = StartupMode.WARM,
      setupBlock = {
        startActivityAndWait()
        navigateTo("Settings")
      },
    ) {
      navigateTo("Explore")
    }
  }
}
