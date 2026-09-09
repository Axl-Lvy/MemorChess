package proj.memorchess.axl.ui.components.explore

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.kotest.assertions.withClue
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import kotlin.test.Test
import proj.memorchess.axl.ui.setKineticContent

/**
 * Guards [ExploreCtrlBar] against the Save label starving Delete of layout space (see #349). A
 * fixed-width [androidx.compose.foundation.layout.Row] does not overflow past its own bounds when a
 * child grows. It shrinks later non-weighted children instead. So a Save button too wide for the
 * row can shrink Delete down to zero size, making it unreachable rather than merely crowding it.
 */
@OptIn(ExperimentalTestApi::class)
class TestExploreCtrlBarWidth {

  private fun noOpActions() =
    ExploreCtrlBarActions(
      onReset = {},
      onReverse = {},
      onBack = {},
      onForward = {},
      onToggleEval = {},
      onSave = {},
      onDelete = {},
    )

  private fun withBarAt(width: Dp, block: (SemanticsNode) -> Unit) = runComposeUiTest {
    setKineticContent {
      Box(modifier = Modifier.width(width)) {
        ExploreCtrlBar(
          modifier = Modifier.testTag("bar"),
          actions = noOpActions(),
          evalEnabled = false,
          playerTurnWhite = true,
        )
      }
    }
    block(onNodeWithTag("bar").fetchSemanticsNode())
  }

  private fun SemanticsNode.childByDescription(description: String) = children.first {
    it.config.getOrNull(SemanticsProperties.ContentDescription)?.contains(description) == true
  }

  // Master already squeezes Delete below its full 36.dp square at this width (the bar's six
  // icon-only controls plus the turn pill are already a few dp over budget here, a pre-existing
  // gap unrelated to #349). This test only guards against the regression #349 could add on top of
  // that: Save's label pushing Delete all the way down to zero and making it unreachable.
  @Test
  fun deleteStaysReachableOnASmallPortraitPhone() =
    withBarAt(344.dp) { bar ->
      withClue("Delete should not collapse to zero width on a narrow phone") {
        bar.childByDescription("Delete").size.width shouldBeGreaterThan 0
      }
    }

  @Test
  fun deleteStaysFullSizeJustBelowTheLabelThreshold() =
    withBarAt(499.dp) { bar ->
      withClue("Delete should keep its full 36.dp square just under the threshold") {
        bar.childByDescription("Delete").size.width shouldBe 36
      }
    }

  @Test
  fun deleteStaysFullSizeOnADesktopWidth() =
    withBarAt(900.dp) { bar ->
      withClue("Delete should keep its full 36.dp square on a wide window") {
        bar.childByDescription("Delete").size.width shouldBe 36
      }
    }

  @Test
  fun saveStaysIconOnlyBelowTheWidthThreshold() =
    withBarAt(344.dp) { bar ->
      withClue("Save should stay icon-only below the width threshold") {
        bar.childByDescription("Save").config.getOrNull(SemanticsProperties.Text) shouldBe null
      }
    }

  @Test
  fun saveShowsItsTextLabelAboveTheWidthThreshold() =
    withBarAt(900.dp) { bar ->
      withClue("Save should show its text label above the width threshold") {
        bar
          .childByDescription("Save")
          .config
          .getOrNull(SemanticsProperties.Text)
          ?.joinToString() shouldBe "SAVE"
      }
    }

  // The tightest labeled case: exactly at the threshold, in English, with room for only 42.dp of
  // slack over the measured minimum. If SAVE_LABEL_MIN_WIDTH is ever lowered past that minimum,
  // this is the test that catches it.
  @Test
  fun saveShowsItsLabelAndDeleteStaysFullSizeAtExactlyTheThreshold() =
    withBarAt(500.dp) { bar ->
      withClue("Save should show its label and Delete should stay full size at the threshold") {
        bar
          .childByDescription("Save")
          .config
          .getOrNull(SemanticsProperties.Text)
          ?.joinToString() shouldBe "SAVE"
        bar.childByDescription("Delete").size.width shouldBe 36
      }
    }
}
