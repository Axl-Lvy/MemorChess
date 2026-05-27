package proj.memorchess.axl.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import proj.memorchess.axl.core.config.APP_THEME_SETTING

/**
 * Top-level theme wrapper for the app. Resolves the active light/dark mode from [APP_THEME_SETTING]
 * (LIGHT / DARK / SYSTEM) and delegates rendering to [KineticTheme].
 *
 * The public API is preserved from the pre-Kinetic implementation so callers don't change — only
 * the internals now wire up Kinetic palette and typography.
 */
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
