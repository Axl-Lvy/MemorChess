package proj.memorchess.axl.core.engine

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlin.test.Test
import proj.memorchess.axl.core.data.PositionKey

class TestGameEngine {

  @Test
  fun playSanMoveOnASanWithNoLegalCandidateThrowsIllegalMoveException() {
    // chess-core throws its own IllegalArgumentException for this case instead of returning
    // false, unlike every other rejection; playSanMove must still honor its documented contract.
    val engine = GameEngine()

    shouldThrow<IllegalMoveException> { engine.playSanMove("Qxe4") }
  }

  @Test
  fun playSanMoveOnAnUnavailableCastleThrowsIllegalMoveException() {
    // chess-core throws NoSuchElementException for this case (no legal move matches the intent),
    // instead of returning false; same contract as the case above.
    val engine = GameEngine()

    shouldThrow<IllegalMoveException> { engine.playSanMove("O-O") }
  }

  @Test
  fun originSquareOfAPlainMoveIsThePawnSquare() {
    GameEngine().originSquare("e4") shouldBe BoardLocation(1, 4)
  }

  @Test
  fun originSquareOfACaptureIsTheCapturingPieceSquare() {
    val engine =
      GameEngine().apply {
        playSanMove("e4")
        playSanMove("d5")
      }

    engine.originSquare("exd5") shouldBe BoardLocation(3, 4)
  }

  @Test
  fun originSquareOfADisambiguatedMoveIsTheNamedPieceSquare() {
    val engine =
      GameEngine().apply {
        playSanMove("d4")
        playSanMove("d5")
        playSanMove("Nf3")
        playSanMove("Nf6")
      }

    engine.originSquare("Nbd2") shouldBe BoardLocation(0, 1)
    engine.originSquare("Nfd2") shouldBe BoardLocation(2, 5)
  }

  @Test
  fun originSquareOfACastleIsTheKingSquareForBothColours() {
    val white = GameEngine(PositionKey("r3k2r/8/8/8/8/8/8/R3K2R w KQkq"))
    val black = GameEngine(PositionKey("r3k2r/8/8/8/8/8/8/R3K2R b KQkq"))

    white.originSquare("O-O") shouldBe BoardLocation(0, 4)
    white.originSquare("O-O-O") shouldBe BoardLocation(0, 4)
    black.originSquare("O-O") shouldBe BoardLocation(7, 4)
    black.originSquare("O-O-O") shouldBe BoardLocation(7, 4)
  }

  @Test
  fun originSquareOfAPromotionIsThePawnSquare() {
    val engine = GameEngine(PositionKey("8/4P3/8/8/8/8/k7/4K3 w -"))

    engine.originSquare("e8=Q") shouldBe BoardLocation(6, 4)
    engine.originSquare("e8=N") shouldBe BoardLocation(6, 4)
  }

  @Test
  fun originSquareIgnoresCheckMarkers() {
    val engine = GameEngine(PositionKey("8/4P3/8/8/8/8/k7/4K3 w -"))

    engine.originSquare("e8=Q+") shouldBe BoardLocation(6, 4)
  }

  @Test
  fun originSquareOfAnIllegalMoveThrowsIllegalMoveException() {
    shouldThrow<IllegalMoveException> { GameEngine().originSquare("e5") }
  }

  @Test
  fun originSquareDoesNotPlayTheMove() {
    val engine = GameEngine()
    val before = engine.toPositionKey()

    engine.originSquare("e4")

    engine.toPositionKey() shouldBe before
  }
}
