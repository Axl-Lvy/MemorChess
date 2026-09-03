package proj.memorchess.axl.core.data

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import kotlin.test.Test

class TestPositionKey {

  @Test
  fun validFenReturnsPositionKey() {
    PositionKey.validateAndCreateOrNull("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq")
      .shouldNotBeNull()
  }

  @Test
  fun invalidFenReturnsNull() {
    PositionKey.validateAndCreateOrNull("not a valid fen").shouldBeNull()
  }
}
