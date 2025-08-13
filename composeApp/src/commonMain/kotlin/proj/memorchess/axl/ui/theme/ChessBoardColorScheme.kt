package proj.memorchess.axl.ui.theme

import androidx.compose.ui.graphics.Color
import proj.memorchess.axl.core.util.CanDisplayName

/**
 * Available color schemes for the chess board.
 *
 * @property lightSquareColor Light square color.
 * @property darkSquareColor Dark square color.
 * @property displayName The name to display for this color scheme.
 * @property selectedBorderColor The color of the border around selected squares.
 */
enum class ChessBoardColorScheme(
  val lightSquareColor: Color,
  val darkSquareColor: Color,
  val selectedBorderColor: Color,
  override val displayName: String,
) : CanDisplayName {
  GRASS(
    lightSquareColor = Color(0xFFEEEED2),
    darkSquareColor = Color(0xff5e803d),
    selectedBorderColor = Color(0xFFB58863),
    displayName = "Grass",
  ),
  SKY(
    lightSquareColor = Color(0xffbae2f8),
    darkSquareColor = Color(0xFF365E73),
    selectedBorderColor = Color(0xff0336e1),
    displayName = "Sky",
  ),
  KAWAII(
    lightSquareColor = Color(0xFFE8D5E8),
    darkSquareColor = Color(0xFF9F7A9F),
    selectedBorderColor = Color(0xFFB72893),
    displayName = "Kawaii",
  ),
  BLACK_AND_WHITE(
    lightSquareColor = Color(0xFFFFFFFF),
    darkSquareColor = Color(0xFF333333),
    selectedBorderColor = Color(0xFFFFA726),
    displayName = "Black",
  ),
  WOOD(
    lightSquareColor = Color(0xFFF0D9B5),
    darkSquareColor = Color(0xff75543c),
    selectedBorderColor = Color(0xFFDE9A04),
    displayName = "Wood",
  ),
}
