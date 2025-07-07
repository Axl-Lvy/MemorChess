package proj.memorchess.axl.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable

@Composable
fun getColorScheme(darkTheme: Boolean): ColorScheme {
  return if (darkTheme) darkColorScheme else lightColorScheme
}

enum class AppThemeSetting {
  SYSTEM,
  LIGHT,
  DARK,
}
