package proj.memorchess.axl.ui.components.buttons

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.down
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.up
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlin.test.Test
import proj.memorchess.axl.core.config.REDUCE_MOTION_SETTING
import proj.memorchess.axl.test_util.TestWithKoin
import proj.memorchess.axl.ui.setKineticContent
import proj.memorchess.axl.ui.theme.KineticLightPalette

private const val BUTTON_TAG = "kinetic_button"

/**
 * Propagation tests for [KineticButton]'s new filled/transparent elevation branch, its square
 * icon-only footprint, and the press paths through the new pressable/scale modifier chain.
 *
 * Extends [TestWithKoin] because pressing a button builds
 * [proj.memorchess.axl.ui.theme.KineticMotion.Routine.buttonPress], which reads
 * [REDUCE_MOTION_SETTING] through Koin.
 */
@OptIn(ExperimentalTestApi::class)
internal class TestKineticButton : TestWithKoin() {

  private fun runButtonTest(block: suspend ComposeUiTest.() -> Unit) = runComposeUiTest {
    koinSetUp()
    try {
      block()
    } finally {
      koinTearDown()
    }
  }

  @Test
  fun everyStyleClicksWhenEnabled() {
    for (style in KineticButtonStyle.entries) {
      runButtonTest {
        var clicks = 0
        setKineticContent {
          KineticButton(
            onClick = { clicks++ },
            style = style,
            modifier = Modifier.testTag(BUTTON_TAG),
          ) {
            KineticButtonLabel("go")
          }
        }
        onNodeWithTag(BUTTON_TAG).performClick()
        waitForIdle()
        withClue(style) { clicks shouldBe 1 }
      }
    }
  }

  @Test
  fun disabledFilledButtonDoesNotClick() = runButtonTest {
    var clicks = 0
    setKineticContent {
      KineticButton(
        onClick = { clicks++ },
        style = KineticButtonStyle.Primary,
        enabled = false,
        modifier = Modifier.testTag(BUTTON_TAG),
      ) {
        KineticButtonLabel("go")
      }
    }
    onNodeWithTag(BUTTON_TAG).performClick()
    waitForIdle()
    clicks shouldBe 0
  }

  @Test
  fun disabledTransparentButtonDoesNotClick() = runButtonTest {
    var clicks = 0
    setKineticContent {
      KineticButton(
        onClick = { clicks++ },
        style = KineticButtonStyle.Ghost,
        enabled = false,
        modifier = Modifier.testTag(BUTTON_TAG),
      ) {
        KineticButtonLabel("go")
      }
    }
    onNodeWithTag(BUTTON_TAG).performClick()
    waitForIdle()
    clicks shouldBe 0
  }

  /**
   * A held press at the very top edge — the position the press translate would move out from under
   * a real finger — still delivers the click. Note this passes with the modifier chain in either
   * order on the desktop host: `performTouchInput` re-resolves the node's bounds on the lift, so
   * the harness never reproduces the drift. It covers the two-phase press path, not the ordering.
   */
  @Test
  fun aHeldPressAtTheTopEdgeStillClicks() = runButtonTest {
    var clicks = 0
    setKineticContent {
      KineticButton(
        onClick = { clicks++ },
        style = KineticButtonStyle.Primary,
        modifier = Modifier.testTag(BUTTON_TAG),
      ) {
        KineticButtonLabel("go")
      }
    }
    // Two separate gestures with an idle in between: the press state has to land and the 3.dp
    // press translate has to be applied before the finger lifts, which is the exact moment a
    // clickable chained after the elevation would slide out from under the stationary pointer.
    onNodeWithTag(BUTTON_TAG).performTouchInput { down(Offset(centerX, 2f)) }
    waitForIdle()
    onNodeWithTag(BUTTON_TAG).performTouchInput { up() }
    waitForIdle()
    clicks shouldBe 1
  }

  @Test
  fun iconOnlyIsSquareAtDefaultSize() = runButtonTest {
    setKineticContent {
      KineticButton(onClick = {}, iconOnly = true, modifier = Modifier.testTag(BUTTON_TAG)) {
        KineticButtonLabel("x")
      }
    }
    onNodeWithTag(BUTTON_TAG).assertWidthIsEqualTo(36.dp).assertHeightIsEqualTo(36.dp)
  }

  @Test
  fun iconOnlyIsSquareAtLargeSize() = runButtonTest {
    setKineticContent {
      KineticButton(
        onClick = {},
        iconOnly = true,
        large = true,
        modifier = Modifier.testTag(BUTTON_TAG),
      ) {
        KineticButtonLabel("x")
      }
    }
    onNodeWithTag(BUTTON_TAG).assertWidthIsEqualTo(44.dp).assertHeightIsEqualTo(44.dp)
  }

  @Test
  fun reduceMotionKeepsTheButtonClickable() = runButtonTest {
    REDUCE_MOTION_SETTING.setValue(true)
    var clicks = 0
    setKineticContent {
      KineticButton(
        onClick = { clicks++ },
        style = KineticButtonStyle.Primary,
        modifier = Modifier.testTag(BUTTON_TAG),
      ) {
        KineticButtonLabel("go")
      }
    }
    onNodeWithTag(BUTTON_TAG).performClick()
    waitForIdle()
    clicks shouldBe 1
  }

  /**
   * [KineticButtonStyle.Danger] fills its entire face with [KineticLightPalette.destructive] —
   * never a plain red. Renders with no text content so every interior pixel samples the fill, not a
   * label glyph; [KineticButton] requires a trailing content lambda but nothing inside it is
   * required to draw anything.
   */
  @Test
  fun dangerStyleFillsWithDestructiveNeverRed() = runButtonTest {
    setKineticContent {
      KineticButton(
        onClick = {},
        style = KineticButtonStyle.Danger,
        modifier = Modifier.testTag(BUTTON_TAG),
      ) {}
    }
    val node = onNodeWithTag(BUTTON_TAG)
    val size = node.fetchSemanticsNode().size
    val pixel = node.captureToImage().toPixelMap()[size.width / 2, size.height / 2]
    pixel shouldBe KineticLightPalette.destructive
    pixel shouldNotBe Color(0xFFFF0000)
  }

  /**
   * [KineticButtonStyle.DangerOutline] strokes its 1.5.dp border in
   * [KineticLightPalette.destructiveDim] — never a plain red. Samples device-pixel column 0 at
   * mid-height (the default 36.dp button is 18.dp tall at its midpoint), away from the rounded
   * corners, where a 1.5px stroke on a straight edge fully covers the first pixel column.
   */
  @Test
  fun dangerOutlineStyleStrokesWithDestructiveDimNeverRed() = runButtonTest {
    setKineticContent {
      KineticButton(
        onClick = {},
        style = KineticButtonStyle.DangerOutline,
        modifier = Modifier.testTag(BUTTON_TAG),
      ) {}
    }
    val midHeight = with(density) { 18.dp.roundToPx() }
    val pixel = onNodeWithTag(BUTTON_TAG).captureToImage().toPixelMap()[0, midHeight]
    pixel shouldBe KineticLightPalette.destructiveDim
    pixel shouldNotBe Color(0xFFFF0000)
  }
}
