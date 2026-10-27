package proj.memorchess.axl.ui.theme

import androidx.compose.ui.graphics.Color
import proj.memorchess.axl.core.util.CanDisplayName

/** Success tint of a few status indicators. */
val goodTint = Color(0xFF4CAF50)

/** User selectable app theme mode. */
enum class AppThemeSetting : CanDisplayName {
  SYSTEM,
  LIGHT,
  DARK;

  override val displayName: String = name
}
