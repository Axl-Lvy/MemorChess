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
 * A fixed width Row shrinks later children instead of overflowing, so a wide Save label could
 * shrink Delete down to zero size.
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

  // Delete is already below its full 36.dp at this width; this only guards it against zero.
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

  // The tightest labeled case, 42.dp over the measured minimum: catches SAVE_LABEL_MIN_WIDTH
  // lowered past that minimum.
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
