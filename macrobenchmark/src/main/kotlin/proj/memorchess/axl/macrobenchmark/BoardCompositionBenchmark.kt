package proj.memorchess.axl.macrobenchmark

import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.TraceSectionMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Measures how long composing the chess board takes when entering the Explore screen.
 *
 * The `BoardGrid` trace sections come from Compose composition tracing: the benchmark build of the
 * app ships `androidx.compose.runtime:runtime-tracing`, and this module enables
 * `androidx.benchmark.fullTracing.enable`, which makes the Compose runtime emit one Perfetto slice
 * per composable. No marker code exists in the app itself.
 *
 * Each iteration starts on Settings (no board) and measures the navigation into Explore, so the
 * board is composed from scratch inside the measured window. Composition tracing adds a small
 * overhead to every composable, so treat the accompanying frame timing numbers as relative
 * indicators only; [ScreenNavigationBenchmark] is the clean frame timing source.
 */
@OptIn(ExperimentalMetricApi::class)
@RunWith(AndroidJUnit4::class)
class BoardCompositionBenchmark {

  @get:Rule val benchmarkRule = MacrobenchmarkRule()

  @Test
  fun boardCompositionOnExploreEntry() {
    benchmarkRule.measureRepeated(
      packageName = TARGET_PACKAGE,
      metrics =
        listOf(TraceSectionMetric("%BoardGrid%", TraceSectionMetric.Mode.Sum), FrameTimingMetric()),
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
