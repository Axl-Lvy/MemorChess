package proj.memorchess.axl.ui.theme

import androidx.compose.ui.graphics.Color
import proj.memorchess.axl.core.util.CanDisplayName

/**
 * Available color schemes for the chess board.
 *
 * @property lightSquareColor Light square color.
 * @property darkSquareColor Dark square color.
 * @property displayName The name to display for this color scheme.
 */
enum class ChessBoardColorScheme(
  val lightSquareColor: Color,
  val darkSquareColor: Color,
  override val displayName: String,
) : CanDisplayName {
  GREEN(
    lightSquareColor = Color(0xFFEEEED2),
    darkSquareColor = Color(0xff5e803d),
    displayName = "Grass",
  ),
  BLUE(
    lightSquareColor = Color(0xffbae2f8),
    darkSquareColor = Color(0xFF365E73),
    displayName = "Sky",
  ),
  PURPLE(
    lightSquareColor = Color(0xFFE8D5E8),
    darkSquareColor = Color(0xFF9F7A9F),
    displayName = "Kawaii",
  ),
  BLACK_AND_WHITE(
    lightSquareColor = Color(0xFFFFFFFF),
    darkSquareColor = Color(0xFF333333),
    displayName = "Black",
  ),
  CLASSIC(
    lightSquareColor = Color(0xFFF0D9B5),
    darkSquareColor = Color(0xff75543c),
    displayName = "Wood",
  ),
}
