package proj.memorchess.axl.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable

@Composable expect fun getDynamicTheme(darkTheme: Boolean): ColorScheme?

@Composable
fun getColorScheme(darkTheme: Boolean): ColorScheme {
  val dynamicColorScheme = getDynamicTheme(darkTheme)
  if (dynamicColorScheme != null) {
    return dynamicColorScheme
  }

  return if (darkTheme) darkColorScheme else lightColorScheme
}

enum class AppThemeSetting {
  SYSTEM,
  LIGHT,
  DARK,
}
