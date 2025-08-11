package proj.memorchess.axl.ui.explore

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import proj.memorchess.axl.core.engine.pieces.Pawn
import proj.memorchess.axl.core.graph.nodes.NodeManager
import proj.memorchess.axl.test_util.TestWithKoin
import proj.memorchess.axl.ui.assertNextMoveExist
import proj.memorchess.axl.ui.assertPieceMoved
import proj.memorchess.axl.ui.clickOnBack
import proj.memorchess.axl.ui.clickOnNext
import proj.memorchess.axl.ui.clickOnReset
import proj.memorchess.axl.ui.clickOnReverse
import proj.memorchess.axl.ui.isBoardReversed
import proj.memorchess.axl.ui.pages.Explore
import proj.memorchess.axl.ui.playMove

@OptIn(ExperimentalTestApi::class)
class TestControlBar : TestWithKoin {

  private val nodeManager = NodeManager()

  private fun runTestFromSetup(block: ComposeUiTest.() -> Unit) {
    runTest { nodeManager.resetCacheFromDataBase() }
    runComposeUiTest {
      setContent { initializeApp { Explore() } }
      playMove("e2", "e4")
      assertPieceMoved("e2", "e4", Pawn.white())
      block()
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

  @Test
  fun testBack() = runTestFromSetup {
    clickOnBack()
    assertPieceMoved("e4", "e2", Pawn.white())
    assertNextMoveExist("e4")
  }

  @Test
  fun testForward() = runTestFromSetup {
    testBack()
    clickOnNext()
    assertPieceMoved("e2", "e4", Pawn.white())
  }

  @Test
  fun testReset() = runTestFromSetup {
    clickOnReset()
    assertPieceMoved("e4", "e2", Pawn.white())
  }
}
