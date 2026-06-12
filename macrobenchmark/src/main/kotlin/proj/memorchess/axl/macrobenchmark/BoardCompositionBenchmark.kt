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
 * Measures how long composing the chess board takes when entering the Explore screen.
 *
 * The `BoardGrid` trace sections come from Compose composition tracing: the benchmark build of the
 * app ships `androidx.compose.runtime:runtime-tracing`, and this module enables
 * `androidx.benchmark.fullTracing.enable`, which makes the Compose runtime emit one Perfetto slice
 * per composable. No marker code exists in the app itself.
 *
 * Each iteration starts on Settings (no board) and measures the navigation into Explore, so the
 * board is composed from scratch inside the measured window. This benchmark deliberately carries no
 * [androidx.benchmark.macro.FrameTimingMetric]: composition tracing skews frame numbers, and on CI
 * emulators the frame timeline regularly produces no expect/actual slices at all, which fails the
 * whole run. [ScreenNavigationBenchmark] is the frame timing source.
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
