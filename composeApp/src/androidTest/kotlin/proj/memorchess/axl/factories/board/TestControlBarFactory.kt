package proj.memorchess.axl.factories.board

import kotlin.test.BeforeTest
import kotlin.test.Test
import proj.memorchess.axl.AUiTestFromMainActivity
import proj.memorchess.axl.core.engine.pieces.Pawn

class TestControlBar : AUiTestFromMainActivity() {

  @BeforeTest
  override fun setUp() {
    super.setUp()
    goToExplore()
    playMove("e2", "e4")
    assertPieceMoved("e2", "e4", Pawn.white())
  }

  @Test
  fun testInvertBoard() {
    clickOnReverse()
    clickOnReverse()
  }

  @Test
  fun testBack() {
    clickOnBack()
    assertPieceMoved("e4", "e2", Pawn.white())
    assertNextMoveExist("e4")
  }

  @Test
  fun testForward() {
    testBack()
    clickOnNext()
    assertPieceMoved("e2", "e4", Pawn.white())
  }

  @Test
  fun testReset() {
    clickOnReset()
    assertPieceMoved("e4", "e2", Pawn.white())
  }
}
