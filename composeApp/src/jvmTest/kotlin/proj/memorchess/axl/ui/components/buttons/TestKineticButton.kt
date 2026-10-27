package proj.memorchess.axl.ui.components.buttons

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
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
import kotlin.test.Test
import proj.memorchess.axl.core.config.REDUCE_MOTION_SETTING
import proj.memorchess.axl.test_util.TestWithKoin
import proj.memorchess.axl.ui.components.settings.sections.DangerZoneSection
import proj.memorchess.axl.ui.setKineticContent
import proj.memorchess.axl.ui.theme.KineticLightPalette

private const val BUTTON_TAG = "kinetic_button"

/** Extends [TestWithKoin] since a press reads [REDUCE_MOTION_SETTING] through Koin. */
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
    // Two gestures, so the press translate applies before the lift. The desktop harness re-resolves
    // bounds on lift, so this covers the two-phase press, not the modifier order.
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

  /** Renders no content, so the centre pixel samples the fill rather than a glyph. */
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
  }

  /** Samples column 0 at mid-height, away from the corners, where the stroke covers it fully. */
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
  }

  /** Samples column 0 at mid-height to land on the fill rather than the centred label. */
  @Test
  fun eraseAllDataButtonInDangerZoneFillsWithDestructive() = runButtonTest {
    setKineticContent { DangerZoneSection(onReset = {}) }
    val midHeight = with(density) { 18.dp.roundToPx() }
    val pixel = onNodeWithTag("eraseAllDataButton").captureToImage().toPixelMap()[0, midHeight]
    pixel shouldBe KineticLightPalette.destructive
  }

  @Test
  fun resetConfigButtonInDangerZoneStrokesWithDestructiveDim() = runButtonTest {
    setKineticContent { DangerZoneSection(onReset = {}) }
    val midHeight = with(density) { 18.dp.roundToPx() }
    val pixel = onNodeWithTag("resetConfigButton").captureToImage().toPixelMap()[0, midHeight]
    pixel shouldBe KineticLightPalette.destructiveDim
  }

  @Test
  fun resetConfigButtonInvokesOnResetAfterConfirmation() = runButtonTest {
    var resetCount = 0
    setKineticContent { DangerZoneSection(onReset = { resetCount++ }) }
    onNodeWithTag("resetConfigButton").performClick()
    onNodeWithTag("confirmDialogOkButton").performClick()
    withClue("onReset should fire exactly once after confirming the reset") {
      resetCount shouldBe 1
    }
  }
}
