package proj.memorchess.axl.core.engine.evaluation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import proj.memorchess.axl.core.engine.BoardLocation

class TestBestMove {

  @Test
  fun parsesStandardMove() {
    val move = BestMove.fromUci("e2e4")
    assertEquals(BestMove(BoardLocation(1, 4), BoardLocation(3, 4)), move)
  }

  @Test
  fun parsesPromotionMoveDroppingTheSuffix() {
    val move = BestMove.fromUci("e7e8q")
    assertEquals(BestMove(BoardLocation(6, 4), BoardLocation(7, 4)), move)
  }

  @Test
  fun returnsNullForTooShortString() {
    assertNull(BestMove.fromUci("e2"))
  }

  @Test
  fun returnsNullForEmptyString() {
    assertNull(BestMove.fromUci(""))
  }

  @Test
  fun returnsNullForOutOfBoundsFromCol() {
    assertNull(BestMove.fromUci("z2e4"))
  }

  @Test
  fun returnsNullForOutOfBoundsFromRow() {
    assertNull(BestMove.fromUci("a9e4"))
  }

  @Test
  fun returnsNullForOutOfBoundsToCol() {
    assertNull(BestMove.fromUci("e2z4"))
  }

  @Test
  fun returnsNullForOutOfBoundsToRow() {
    assertNull(BestMove.fromUci("e2e9"))
  }

  @Test
  fun parsesCornerToCornerMove() {
    val move = BestMove.fromUci("a1h8")
    assertEquals(BestMove(BoardLocation(0, 0), BoardLocation(7, 7)), move)
  }
}
