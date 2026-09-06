package proj.memorchess.axl.ui.components.training

import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertRangeInfoEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import proj.memorchess.axl.ui.setKineticContent

private const val RAIL_TAG = "progress_rail"

/**
 * Pins [KineticProgressRail]'s clamping contract: `progress` is coerced into `0f..1f` and every
 * non-finite input falls back to `0f`. The clamped value is read back through the rail's reported
 * [ProgressBarRangeInfo], so nothing test-only is added to the production API.
 */
@OptIn(ExperimentalTestApi::class)
class TestKineticProgressRail {

  private fun ComposeUiTest.assertClampsTo(input: Float, expected: Float) {
    setKineticContent {
      KineticProgressRail(progress = input, modifier = Modifier.testTag(RAIL_TAG))
    }
    onNodeWithTag(RAIL_TAG).assertRangeInfoEquals(ProgressBarRangeInfo(expected, 0f..1f))
  }

  @Test fun zeroIsReportedAsZero() = runComposeUiTest { assertClampsTo(0f, 0f) }

  @Test
  fun smallestPositiveValueIsKept() = runComposeUiTest {
    assertClampsTo(Float.MIN_VALUE, Float.MIN_VALUE)
  }

  @Test fun halfIsKept() = runComposeUiTest { assertClampsTo(0.5f, 0.5f) }

  @Test fun oneIsReportedAsOne() = runComposeUiTest { assertClampsTo(1f, 1f) }

  @Test fun aboveOneIsClampedToOne() = runComposeUiTest { assertClampsTo(1.5f, 1f) }

  @Test fun belowZeroIsClampedToZero() = runComposeUiTest { assertClampsTo(-0.5f, 0f) }

  @Test fun nanFallsBackToZero() = runComposeUiTest { assertClampsTo(Float.NaN, 0f) }

  @Test
  fun positiveInfinityFallsBackToZero() = runComposeUiTest {
    assertClampsTo(Float.POSITIVE_INFINITY, 0f)
  }

  @Test
  fun negativeInfinityFallsBackToZero() = runComposeUiTest {
    assertClampsTo(Float.NEGATIVE_INFINITY, 0f)
  }

  @Test
  fun railRendersAtZeroWidth() = runComposeUiTest {
    setKineticContent {
      KineticProgressRail(progress = 0.5f, modifier = Modifier.width(0.dp).testTag(RAIL_TAG))
    }
    onNodeWithTag(RAIL_TAG).assertRangeInfoEquals(ProgressBarRangeInfo(0.5f, 0f..1f))
  }
}
