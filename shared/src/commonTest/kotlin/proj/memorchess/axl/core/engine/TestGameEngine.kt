package proj.memorchess.axl.core.engine

import io.kotest.assertions.throwables.shouldThrow
import kotlin.test.Test

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
}
