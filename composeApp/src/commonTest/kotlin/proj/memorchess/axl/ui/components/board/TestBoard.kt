package proj.memorchess.axl.ui.components.board

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import io.kotest.matchers.shouldBe
import kotlin.test.Test
import org.koin.core.component.inject
import proj.memorchess.axl.core.graph.RepertoireTagStore
import proj.memorchess.axl.core.graph.TreeStore
import proj.memorchess.axl.core.interactions.LinesExplorer
import proj.memorchess.axl.test_util.TestWithKoin
import proj.memorchess.axl.ui.isBoardReversed
import proj.memorchess.axl.ui.setTestContent

@OptIn(ExperimentalTestApi::class)
class TestBoard : TestWithKoin() {

  private val treeStore: TreeStore by inject()
  private val tagStore: RepertoireTagStore by inject()

  private fun runBoard(inverted: Boolean, block: suspend ComposeUiTest.() -> Unit) =
    runComposeUiTest {
      koinSetUp()
      try {
        val explorer = LinesExplorer(treeStore = treeStore, tagStore = tagStore)
        // Positional on purpose, to pin Board's parameter order.
        setTestContent { InitializeApp { Board(explorer, inverted) } }
        block()
      } finally {
        koinTearDown()
      }
    }

  @Test
  fun boardRendersUprightWhenNotInverted() =
    runBoard(inverted = false) { isBoardReversed() shouldBe false }

  @Test
  fun boardRendersInvertedWhenRequested() =
    runBoard(inverted = true) { isBoardReversed() shouldBe true }
}
