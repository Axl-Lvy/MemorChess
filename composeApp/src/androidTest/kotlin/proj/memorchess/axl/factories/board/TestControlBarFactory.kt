package proj.memorchess.axl.factories.board

import proj.memorchess.axl.AUiTestFactory
import proj.memorchess.axl.core.engine.pieces.Pawn

class TestControlBarFactory : AUiTestFactory() {
  override fun createTests(): List<() -> Unit> {
    return listOf(::testInvertBoard, ::testBack, ::testForward, ::testReset)
  }

  override fun beforeEach() {
    goToExplore()
    playMove("e2", "e4")
    assertPieceMoved("e2", "e4", Pawn.white())
  }

  fun testInvertBoard() {
    clickOnReverse()
    clickOnReverse()
  }

  fun testBack() {
    clickOnBack()
    assertPieceMoved("e4", "e2", Pawn.white())
    assertNextMoveExist("e4")
  }

  fun testForward() {
    testBack()
    clickOnNext()
    assertPieceMoved("e2", "e4", Pawn.white())
  }

  fun testReset() {
    clickOnReset()
    assertPieceMoved("e4", "e2", Pawn.white())
  }
}
