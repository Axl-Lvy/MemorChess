package proj.memorchess.axl.ui.components.training

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import proj.memorchess.axl.ui.setKineticContent

/**
 * Covers [KineticCounterBlock]'s KDoc promise that any [Int] renders through [Int.toString], plus
 * one case per [KineticCounterTone] arm.
 */
@OptIn(ExperimentalTestApi::class)
class TestKineticCounterBlock {

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
}
