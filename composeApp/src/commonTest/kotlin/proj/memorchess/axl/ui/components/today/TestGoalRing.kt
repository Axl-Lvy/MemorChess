package proj.memorchess.axl.ui.components.today

import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertRangeInfoEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import proj.memorchess.axl.ui.setKineticContent

private const val RING_TAG = "goal_ring"

/**
 * Pins [GoalRing]'s clamping contract: `progress` is coerced into `0f..1f` and every non-finite
 * input falls back to `0f`. Mirrors
 * [proj.memorchess.axl.ui.components.training.TestKineticProgressRail]'s exact matrix, since
 * [GoalRing] follows the same contract adapted from a bar to a ring.
 */
@OptIn(ExperimentalTestApi::class)
class TestGoalRing {

  private fun ComposeUiTest.assertClampsTo(input: Float, expected: Float) {
    setKineticContent { GoalRing(progress = input, modifier = Modifier.testTag(RING_TAG)) }
    onNodeWithTag(RING_TAG).assertRangeInfoEquals(ProgressBarRangeInfo(expected, 0f..1f))
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
}
