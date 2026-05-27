package proj.memorchess.axl.ui.theme

import androidx.compose.ui.graphics.Color
import proj.memorchess.axl.core.util.CanDisplayName

/** Legacy success tint used by a few status indicators. Kept until those callers are restyled. */
val goodTint = Color(0xFF4CAF50)

/** User-selectable app theme mode. Persisted via `APP_THEME_SETTING`. */
enum class AppThemeSetting : CanDisplayName {
  SYSTEM,
  LIGHT,
  DARK;

  override val displayName: String = name
}
