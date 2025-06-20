package proj.memorchess.axl.factories.board

import androidx.compose.ui.test.performClick
import proj.memorchess.axl.AUiTestFactory
import proj.memorchess.axl.core.engine.pieces.Pawn

class TestNextMoveBarFactory : AUiTestFactory() {
  override fun createTests(): List<() -> Unit> {
    return listOf(
      ::testNextMoveAppears,
      ::testNextMoveWorks,
      ::testMultipleNextMoves,
      ::testNextMoveAfterReset,
    )
  }

  override fun beforeEach() {
    goToExplore()
    playMove("e2", "e4")
    assertPieceMoved("e2", "e4", Pawn.white())
  }

  fun testNextMoveAppears() {
    clickOnBack()
    assertNextMoveExist("e4")
  }

  fun testNextMoveWorks() {
    clickOnBack()
    assertNextMoveExist("e4").performClick()
    assertPieceMoved("e2", "e4", Pawn.white())
  }

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

  fun testNextMoveAfterReset() {
    clickOnReset()
    assertNextMoveExist("e4").performClick()
    assertPieceMoved("e2", "e4", Pawn.white())
  }
}
