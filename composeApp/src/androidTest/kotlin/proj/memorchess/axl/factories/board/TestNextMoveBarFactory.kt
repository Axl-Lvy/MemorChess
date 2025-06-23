package proj.memorchess.axl.factories.board

import androidx.compose.ui.test.performClick
import proj.memorchess.axl.AUiTestFactory
import proj.memorchess.axl.core.engine.pieces.Pawn
import proj.memorchess.axl.util.UiTest

class TestNextMoveBarFactory : AUiTestFactory() {

  override fun beforeEach() {
    goToExplore()
    playMove("e2", "e4")
    assertPieceMoved("e2", "e4", Pawn.white())
  }

  @UiTest
  fun testNextMoveAppears() {
    clickOnBack()
    assertNextMoveExist("e4")
  }

  @UiTest
  fun testNextMoveWorks() {
    clickOnBack()
    assertNextMoveExist("e4").performClick()
    assertPieceMoved("e2", "e4", Pawn.white())
  }

  @UiTest
  fun testMultipleNextMoves() {
    clickOnBack()
    playMove("e2", "e3")
    assertPieceMoved("e2", "e3", Pawn.white())
    clickOnBack()
    assertNextMoveExist("e4")
    assertNextMoveExist("e3").performClick()
    assertPieceMoved("e2", "e3", Pawn.white())
    clickOnBack()
    assertNextMoveExist("e3")
    assertNextMoveExist("e4").performClick()
    assertPieceMoved("e2", "e4", Pawn.white())
  }

  @UiTest
  fun testNextMoveAfterReset() {
    clickOnReset()
    assertNextMoveExist("e4").performClick()
    assertPieceMoved("e2", "e4", Pawn.white())
  }
}
