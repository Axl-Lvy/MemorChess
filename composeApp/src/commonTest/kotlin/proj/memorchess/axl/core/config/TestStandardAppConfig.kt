package proj.memorchess.axl.core.config

import io.kotest.matchers.shouldBe
import kotlin.test.Test
import proj.memorchess.axl.test_util.TestWithKoin
import proj.memorchess.axl.ui.theme.ChessBoardColorScheme

/** Pins the out-of-the-box board style: on-brand violet, not a generic skin. */
class TestStandardAppConfig : TestWithKoin() {

  @Test
  fun boardColorDefaultsToTheVioletKineticScheme() = test {
    CHESS_BOARD_COLOR_SETTING.getValue() shouldBe ChessBoardColorScheme.KINETIC_VIOLET
  }
}
