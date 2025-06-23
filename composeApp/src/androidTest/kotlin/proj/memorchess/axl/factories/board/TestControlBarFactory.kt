package proj.memorchess.axl.factories.board

import proj.memorchess.axl.AUiTestFactory
import proj.memorchess.axl.core.engine.pieces.Pawn
import proj.memorchess.axl.util.UiTest

class TestControlBarFactory : AUiTestFactory() {

  override fun beforeEach() {
    goToExplore()
    playMove("e2", "e4")
    assertPieceMoved("e2", "e4", Pawn.white())
  }

  @UiTest
  fun testInvertBoard() {
    clickOnReverse()
    clickOnReverse()
  }

  @UiTest
  fun testBack() {
    clickOnBack()
    assertPieceMoved("e4", "e2", Pawn.white())
    assertNextMoveExist("e4")
  }

  @UiTest
  fun testForward() {
    testBack()
    clickOnNext()
    assertPieceMoved("e2", "e4", Pawn.white())
  }

  @UiTest
  fun testReset() {
    clickOnReset()
    assertPieceMoved("e4", "e2", Pawn.white())
  }
}
