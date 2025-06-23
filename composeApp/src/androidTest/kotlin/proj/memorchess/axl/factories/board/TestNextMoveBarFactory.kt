package proj.memorchess.axl.factories.board

import androidx.compose.ui.test.performClick
import kotlin.test.BeforeTest
import kotlin.test.Test
import proj.memorchess.axl.AUiTestFromMainActivity
import proj.memorchess.axl.core.engine.pieces.Pawn

class TestNextMoveBar : AUiTestFromMainActivity() {

  @BeforeTest
  override fun setUp() {
    super.setUp()
    goToExplore()
    playMove("e2", "e4")
    assertPieceMoved("e2", "e4", Pawn.white())
  }

  @Test
  fun testNextMoveAppears() {
    clickOnBack()
    assertNextMoveExist("e4")
  }

  @Test
  fun testNextMoveWorks() {
    clickOnBack()
    assertNextMoveExist("e4").performClick()
    assertPieceMoved("e2", "e4", Pawn.white())
  }

  @Test
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

  @Test
  fun testNextMoveAfterReset() {
    clickOnReset()
    assertNextMoveExist("e4").performClick()
    assertPieceMoved("e2", "e4", Pawn.white())
  }
}
