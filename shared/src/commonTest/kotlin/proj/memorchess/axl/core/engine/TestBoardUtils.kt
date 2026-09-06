package proj.memorchess.axl.core.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import proj.memorchess.axl.core.data.PositionKey

class TestBoardUtils {

  @Test
  fun destinationSquareOnAPlainPawnMoveReadsTheTrailingSquare() {
    assertEquals(BoardLocation(3, 4), BoardUtils.destinationSquare("e4", whiteToMove = true))
  }

  @Test
  fun destinationSquareOnACaptureReadsTheTrailingSquare() {
    assertEquals(BoardLocation(4, 3), BoardUtils.destinationSquare("exd5", whiteToMove = true))
  }

  @Test
  fun destinationSquareOnADisambiguatedMoveReadsTheTrailingSquare() {
    assertEquals(BoardLocation(6, 3), BoardUtils.destinationSquare("Nbd7", whiteToMove = false))
  }

  @Test
  fun destinationSquareOnAPromotionStripsTheEqualsSuffix() {
    assertEquals(BoardLocation(7, 4), BoardUtils.destinationSquare("e8=Q", whiteToMove = true))
  }

  @Test
  fun destinationSquareOnACaptureCombinedWithPromotionSurvivesBothTransforms() {
    // Both the '=' suffix and a non-trailing 'x' capture marker must survive the
    // substringBefore('=') + takeLast(2) pair together.
    assertEquals(BoardLocation(7, 3), BoardUtils.destinationSquare("exd8=Q", whiteToMove = true))
  }

  @Test
  fun destinationSquareOnTheLowCoordinateBoundary() {
    assertEquals(BoardLocation(0, 0), BoardUtils.destinationSquare("a1", whiteToMove = true))
  }

  @Test
  fun destinationSquareOnTheHighCoordinateBoundary() {
    assertEquals(BoardLocation(7, 7), BoardUtils.destinationSquare("h8", whiteToMove = true))
    assertEquals(BoardLocation(7, 7), BoardUtils.destinationSquare("Qxh8", whiteToMove = true))
  }

  @Test
  fun destinationSquareOnCastlingLiterals() {
    assertEquals(BoardLocation(0, 6), BoardUtils.destinationSquare("O-O", whiteToMove = true))
    assertEquals(BoardLocation(0, 2), BoardUtils.destinationSquare("O-O-O", whiteToMove = true))
    assertEquals(BoardLocation(7, 6), BoardUtils.destinationSquare("O-O", whiteToMove = false))
    assertEquals(BoardLocation(7, 2), BoardUtils.destinationSquare("O-O-O", whiteToMove = false))
  }

  @Test
  fun destinationSquareOnARealCastleConfirmsChessCoreUsesTheLetterOForCastling() {
    // Rests the whole wrong-answer path for a castled repertoire move on this one fact: chess-core
    // notates castling with the letter 'O', not the digit '0'.
    val engine = GameEngine(PositionKey("4k3/8/8/8/8/8/8/R3K2R w KQ"))
    val san = engine.playCoordinateMove(Pair(0, 4), Pair(0, 6))

    assertEquals(BoardLocation(0, 6), BoardUtils.destinationSquare(san, whiteToMove = true))
  }
}
