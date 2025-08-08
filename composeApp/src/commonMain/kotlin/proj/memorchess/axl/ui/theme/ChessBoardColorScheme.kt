package proj.memorchess.axl.ui.theme

import androidx.compose.ui.graphics.Color
import proj.memorchess.axl.core.util.CanDisplayName

/** Available chess board color schemes */
enum class ChessBoardColorScheme(
  val lightSquareColor: Color,
  val darkSquareColor: Color,
  override val displayName: String,
) : CanDisplayName {
  GREEN(
    lightSquareColor = Color(0xFFEEEED2), // Light cream
    darkSquareColor = Color(0xff5e803d), // Forest green
    displayName = "Grass",
  ),
  BLUE(
    lightSquareColor = Color(0xffbae2f8), // Light blue-gray
    darkSquareColor = Color(0xFF365E73), // Blue-gray
    displayName = "Sky",
  ),
  PURPLE(
    lightSquareColor = Color(0xFFE8D5E8), // Light purple
    darkSquareColor = Color(0xFF9F7A9F), // Purple
    displayName = "Kawaii",
  ),
  BLACK_AND_WHITE(
    lightSquareColor = Color(0xFFFFFFFF), // Pure white
    darkSquareColor = Color(0xFF333333), // Dark gray for better contrast
    displayName = "Black and White",
  ),
  CLASSIC(
    lightSquareColor = Color(0xFFF0D9B5), // Light beige
    darkSquareColor = Color(0xff75543c), // Dark brown
    displayName = "Classic",
  ),
}
