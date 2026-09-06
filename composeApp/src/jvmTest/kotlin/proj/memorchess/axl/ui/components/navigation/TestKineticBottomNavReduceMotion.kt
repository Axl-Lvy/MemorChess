package proj.memorchess.axl.ui.components.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlin.test.Test
import proj.memorchess.axl.core.config.REDUCE_MOTION_SETTING
import proj.memorchess.axl.test_util.TestWithKoin

/**
 * Pixel-level coverage for [NavCellIcon]'s pop [androidx.compose.animation.core.Animatable], the
 * one thing [TestKineticBottomNav]'s semantics-level assertions cannot observe (`graphicsLayer`
 * scale is a draw-phase transform invisible to the semantics tree). Composes [NavCellIcon]
 * directly, wrapped bare in a `Box` with no pill or label, so nothing else animating under reduce
 * motion (the pill's own colour fade) can change the captured pixels for a reason unrelated to the
 * icon's scale.
 */
@OptIn(ExperimentalTestApi::class)
internal class TestKineticBottomNavReduceMotion : TestWithKoin() {

  private val testItem = NavigationBarItemContent.Explore

  private fun runIconTest(block: suspend ComposeUiTest.() -> Unit) = runComposeUiTest {
    koinSetUp()
    try {
      block()
    } finally {
      koinTearDown()
    }
  }

  @Test
  fun reduceMotionShowsNoIntermediatePopFrame() = runIconTest {
    REDUCE_MOTION_SETTING.setValue(true)
    mainClock.autoAdvance = false
    val active = mutableStateOf(false)
    setContent {
      InitializeApp {
        Box(Modifier.testTag("icon")) {
          NavCellIcon(item = testItem, tint = Color.Black, active = active.value)
        }
      }
    }
    mainClock.advanceTimeByFrame()

    active.value = true
    mainClock.advanceTimeByFrame() // active flips here; scale still 1f in both modes
    val justAfterFlip = onNodeWithTag("icon").captureToImage().toPixelMap().buffer

    mainClock.advanceTimeBy(180) // spans where a full-motion pop would be near its 1.12 peak
    val midWindow = onNodeWithTag("icon").captureToImage().toPixelMap().buffer

    mainClock.advanceTimeBy(1000) // well past any spec's max duration
    val settled = onNodeWithTag("icon").captureToImage().toPixelMap().buffer

    // Identical pixel for pixel: proves the icon never moved, not just that it is selected.
    midWindow shouldBe justAfterFlip
    settled shouldBe justAfterFlip
  }

  @Test
  fun fullMotionShowsAnIntermediatePopFrame() = runIconTest {
    mainClock.autoAdvance = false
    val active = mutableStateOf(false)
    setContent {
      InitializeApp {
        Box(Modifier.testTag("icon")) {
          NavCellIcon(item = testItem, tint = Color.Black, active = active.value)
        }
      }
    }
    mainClock.advanceTimeByFrame()

    active.value = true
    mainClock.advanceTimeByFrame()
    val justAfterFlip = onNodeWithTag("icon").captureToImage().toPixelMap().buffer

    mainClock.advanceTimeBy(180)
    val midWindow = onNodeWithTag("icon").captureToImage().toPixelMap().buffer

    midWindow shouldNotBe justAfterFlip
  }
}
