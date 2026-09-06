package proj.memorchess.axl.ui.theme

import androidx.compose.ui.graphics.Color
import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlin.test.Test

/**
 * Pins the best-move arrow's palette role. The engine suggestion rides `progress`, so it is lime in
 * dark and violet in light, while square selection keeps the interactive `action` role. The
 * user-chosen skins keep their own hues and must not be swept along.
 */
internal class TestChessBoardColorScheme {

  @Test
  fun kineticDarkArrowUsesProgress() {
    ChessBoardColorScheme.KINETIC_DARK.arrowColor shouldBe
      KineticDarkPalette.progress.copy(alpha = 0.5f)
  }

  @Test
  fun kineticLightArrowUsesProgress() {
    ChessBoardColorScheme.KINETIC_LIGHT.arrowColor shouldBe
      KineticLightPalette.progress.copy(alpha = 0.5f)
  }

  @Test
  fun kineticArrowsKeepHalfAlpha() {
    // `Color.copy` quantises alpha to 8 bits, so half round-trips as 128/255.
    ChessBoardColorScheme.KINETIC_DARK.arrowColor.alpha shouldBe (0.5f plusOrMinus 0.005f)
    ChessBoardColorScheme.KINETIC_LIGHT.arrowColor.alpha shouldBe (0.5f plusOrMinus 0.005f)
  }

  @Test
  fun kineticDarkArrowIsNotTheSelectionColor() {
    ChessBoardColorScheme.KINETIC_DARK.arrowColor shouldNotBe
      ChessBoardColorScheme.KINETIC_DARK.selectedBorderColor
  }

  @Test
  fun nonKineticSchemesKeepTheirOwnArrowHues() {
    ChessBoardColorScheme.GRASS.arrowColor shouldBe Color(0x80B58863)
    ChessBoardColorScheme.SKY.arrowColor shouldBe Color(0x800336E1)
    ChessBoardColorScheme.KAWAII.arrowColor shouldBe Color(0x80B72893)
    ChessBoardColorScheme.BLACK_AND_WHITE.arrowColor shouldBe Color(0x80FFA726)
    ChessBoardColorScheme.WOOD.arrowColor shouldBe Color(0x80DE9A04)
  }
}
