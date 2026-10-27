package proj.memorchess.axl.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import proj.memorchess.axl.core.config.APP_THEME_SETTING

/** Top-level app theme, in the light or dark mode [APP_THEME_SETTING] selects. */
@Composable
fun AppTheme(app: @Composable () -> Unit) {
  val appThemeSetting = APP_THEME_SETTING.getValue()
  val darkTheme =
    when (appThemeSetting) {
      AppThemeSetting.LIGHT -> false
      AppThemeSetting.DARK -> true
      AppThemeSetting.SYSTEM -> isSystemInDarkTheme()
    }
  KineticTheme(darkTheme = darkTheme) { app() }
}
