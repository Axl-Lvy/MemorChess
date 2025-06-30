package proj.memorchess.axl.explore

import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import proj.memorchess.axl.core.engine.pieces.Pawn
import proj.memorchess.axl.utils.AUiTestFromMainActivity

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
    assertFalse { isBoardReversed() }
    clickOnReverse()
    assertTrue { isBoardReversed() }
    clickOnReverse()
    assertFalse { isBoardReversed() }
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
