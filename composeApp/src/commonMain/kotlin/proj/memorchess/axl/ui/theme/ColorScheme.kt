package proj.memorchess.axl.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import proj.memorchess.axl.core.util.CanDisplayName

@Composable
fun getColorScheme(darkTheme: Boolean): ColorScheme {
  return if (darkTheme) darkColorScheme else lightColorScheme
}

val goodTint = Color(0xFF4CAF50)

enum class AppThemeSetting() : CanDisplayName {
  SYSTEM,
  LIGHT,
  DARK;

  override val displayName = this.name
}
