package proj.memorchess.axl.explore

import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import kotlin.test.BeforeTest
import kotlin.test.Test
import proj.memorchess.axl.core.engine.pieces.Pawn
import proj.memorchess.axl.utils.AUiTestFromMainActivity

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
  fun testScrollToLastNextMove() {
    clickOnBack()
    for (col in listOf("a", "b", "c", "d", "e", "f", "g", "h")) {
      playMove("${col}2", "${col}3")
      clickOnBack()
      playMove("${col}2", "${col}4")
      clickOnBack()
    }
    assertNextMoveExist("a3")
    assertNextMoveExist("h4").performScrollTo().performClick()
    assertPieceMoved("h2", "h4", Pawn.white())
  }

  @Test
  fun testNextMoveAfterReset() {
    clickOnReset()
    assertNextMoveExist("e4").performClick()
    assertPieceMoved("e2", "e4", Pawn.white())
  }
}
