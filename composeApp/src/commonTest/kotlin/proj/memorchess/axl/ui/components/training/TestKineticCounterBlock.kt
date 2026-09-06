package proj.memorchess.axl.ui.components.training

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import proj.memorchess.axl.core.config.REDUCE_MOTION_SETTING
import proj.memorchess.axl.test_util.TestWithKoin
import proj.memorchess.axl.ui.setKineticContent
import proj.memorchess.axl.ui.theme.KineticMotion

/**
 * Covers [KineticCounterBlock]'s KDoc promise that any [Int] renders through [Int.toString], plus
 * one case per [KineticCounterTone] arm.
 *
 * The eight value/tone tests below never pass `animateOnChange`, so they exercise only the default
 * `false` path (a plain `Text`), which short-circuits before
 * [KineticMotion.shouldAnimateBoardFeedback] (and therefore Koin) is ever reached. That is what
 * keeps them Koin-free, while the two `animateOnChange = true` tests at the bottom need
 * [TestWithKoin]'s lifecycle.
 */
@OptIn(ExperimentalTestApi::class)
class TestKineticCounterBlock : TestWithKoin() {

  private fun ComposeUiTest.assertRenders(value: Int, expected: String) {
    setKineticContent {
      KineticCounterBlock(label = "success", value = value, tone = KineticCounterTone.Success)
    }
    onNodeWithText("SUCCESS").assertIsDisplayed()
    onNodeWithText(expected).assertIsDisplayed()
  }

  @Test fun zeroRenders() = runComposeUiTest { assertRenders(0, "0") }

  @Test fun oneRenders() = runComposeUiTest { assertRenders(1, "1") }

  @Test fun minusOneRenders() = runComposeUiTest { assertRenders(-1, "-1") }

  @Test fun maxIntRenders() = runComposeUiTest { assertRenders(Int.MAX_VALUE, "2147483647") }

  @Test fun minIntRenders() = runComposeUiTest { assertRenders(Int.MIN_VALUE, "-2147483648") }

  private fun ComposeUiTest.assertToneRenders(tone: KineticCounterTone) {
    setKineticContent { KineticCounterBlock(label = "left", value = 42, tone = tone) }
    onNodeWithText("LEFT").assertIsDisplayed()
    onNodeWithText("42").assertIsDisplayed()
  }

  @Test
  fun successToneRenders() = runComposeUiTest { assertToneRenders(KineticCounterTone.Success) }

  @Test fun failToneRenders() = runComposeUiTest { assertToneRenders(KineticCounterTone.Fail) }

  @Test
  fun neutralToneRenders() = runComposeUiTest { assertToneRenders(KineticCounterTone.Neutral) }

  @Test
  fun animatedCounterSettlesOnNewValueWhenAnimateOnChangeIsTrue() = runComposeUiTest {
    koinSetUp()
    try {
      var value by mutableStateOf(0)
      setKineticContent {
        KineticCounterBlock(
          label = "success",
          value = value,
          tone = KineticCounterTone.Success,
          animateOnChange = true,
        )
      }
      onNodeWithText("0").assertIsDisplayed()

      value = 1

      onNodeWithText("1").assertIsDisplayed()
      onNodeWithText("0").assertDoesNotExist()
    } finally {
      koinTearDown()
    }
  }

  @Test
  fun animatedCounterShowsTheNewValueImmediatelyUnderReducedMotion() = runComposeUiTest {
    koinSetUp()
    try {
      REDUCE_MOTION_SETTING.setValue(true)
      var value by mutableStateOf(0)
      setKineticContent {
        KineticCounterBlock(
          label = "success",
          value = value,
          tone = KineticCounterTone.Success,
          animateOnChange = true,
        )
      }
      onNodeWithText("0").assertIsDisplayed()

      value = 1

      onNodeWithText("1").assertIsDisplayed()
      onNodeWithText("0").assertDoesNotExist()
    } finally {
      koinTearDown()
    }
  }
}
