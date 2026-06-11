package proj.memorchess.axl.ui.explore

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.koin.core.component.inject
import proj.memorchess.axl.core.engine.ChessPiece
import proj.memorchess.axl.core.engine.PieceKind
import proj.memorchess.axl.core.engine.Player
import proj.memorchess.axl.core.graph.TreeStore
import proj.memorchess.axl.test_util.TestWithKoin
import proj.memorchess.axl.ui.assertPieceMoved
import proj.memorchess.axl.ui.clickOnBack
import proj.memorchess.axl.ui.clickOnNext
import proj.memorchess.axl.ui.clickOnReset
import proj.memorchess.axl.ui.clickOnReverse
import proj.memorchess.axl.ui.isBoardReversed
import proj.memorchess.axl.ui.pages.Explore
import proj.memorchess.axl.ui.playMove

@OptIn(ExperimentalTestApi::class)
class TestControlBar : TestWithKoin() {

  private val treeStore: TreeStore by inject()

  private fun runTestFromSetup(block: ComposeUiTest.() -> Unit) = runComposeUiTest {
    koinSetUp()
    try {
      treeStore.load()
      setContent { InitializeApp { Explore() } }
      playMove("e2", "e4")
      assertPieceMoved("e2", "e4", ChessPiece(PieceKind.PAWN, Player.WHITE))
      block()
    } finally {
      koinTearDown()
    }
  }

  @Test
  fun testInvertBoard() = runTestFromSetup {
    assertFalse { isBoardReversed() }
    clickOnReverse()
    assertTrue { isBoardReversed() }
    clickOnReverse()
    assertFalse { isBoardReversed() }
  }

  @Test fun testBack() = runTestFromSetup { playThenBack() }

  @Test
  fun testForward() = runTestFromSetup {
    playThenBack()
    clickOnNext()
    assertPieceMoved("e2", "e4", ChessPiece(PieceKind.PAWN, Player.WHITE))
  }

  private fun ComposeUiTest.playThenBack() {
    clickOnBack()
    assertPieceMoved("e4", "e2", ChessPiece(PieceKind.PAWN, Player.WHITE))
  }

  @Test
  fun testReset() = runTestFromSetup {
    clickOnReset()
    assertPieceMoved("e4", "e2", ChessPiece(PieceKind.PAWN, Player.WHITE))
  }
}
