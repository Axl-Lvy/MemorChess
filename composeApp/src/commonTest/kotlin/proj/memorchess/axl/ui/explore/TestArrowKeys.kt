package proj.memorchess.axl.ui.explore

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import proj.memorchess.axl.core.engine.ChessPiece
import proj.memorchess.axl.core.engine.PieceKind
import proj.memorchess.axl.core.engine.Player
import proj.memorchess.axl.test_util.TestWithKoin
import proj.memorchess.axl.ui.assertPieceMoved
import proj.memorchess.axl.ui.pages.Explore
import proj.memorchess.axl.ui.playMove
import proj.memorchess.axl.ui.setTestContent

@OptIn(ExperimentalTestApi::class)
class TestArrowKeys : TestWithKoin() {

  private val whitePawn = ChessPiece(PieceKind.PAWN, Player.WHITE)
  private val blackPawn = ChessPiece(PieceKind.PAWN, Player.BLACK)

  private fun runTestFromSetup(block: suspend ComposeUiTest.() -> Unit) = runComposeUiTest {
    koinSetUp()
    try {
      setTestContent { InitializeApp { Explore() } }
      playMove("e2", "e4")
      playMove("e7", "e5")
      assertPieceMoved("e7", "e5", blackPawn)
      block()
    } finally {
      koinTearDown()
    }
  }

  private suspend fun ComposeUiTest.press(key: Key) {
    onRoot().performKeyInput { pressKey(key) }
    awaitIdle()
  }

  @Test
  fun testLeftAndRightStepThroughTheLine() = runTestFromSetup {
    press(Key.DirectionLeft)

    assertPieceMoved("e5", "e7", blackPawn)
    assertPieceMoved("e2", "e4", whitePawn)

    press(Key.DirectionRight)

    assertPieceMoved("e7", "e5", blackPawn)
  }

  @Test
  fun testDownAndUpJumpToFirstAndLast() = runTestFromSetup {
    press(Key.DirectionDown)

    assertPieceMoved("e4", "e2", whitePawn)
    assertPieceMoved("e5", "e7", blackPawn)

    press(Key.DirectionUp)

    assertPieceMoved("e2", "e4", whitePawn)
    assertPieceMoved("e7", "e5", blackPawn)
  }
}
