package proj.memorchess.axl.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * CompositionLocal exposing the active [KineticPalette]. Defaults to the dark palette so accidental
 * usage outside of [KineticTheme] still renders something sensible — callers should always wrap UI
 * in [KineticTheme] (or [AppTheme]) for the correct palette to be provided.
 */
val LocalKineticPalette = staticCompositionLocalOf { KineticDarkPalette }

/**
 * Provides the Kinetic design system to its content: palette via [LocalKineticPalette], typography
 * via [LocalKineticTypography], and a Material 3 [MaterialTheme] derived from the same tokens so
 * existing M3 widgets remain consistent until they are migrated to bespoke Kinetic Composables.
 *
 * Prefer [AppTheme] as the application entry point; [KineticTheme] is the lower-level building
 * block that can also be used in previews or tests.
 */
@Composable
fun KineticTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
  val palette = if (darkTheme) KineticDarkPalette else KineticLightPalette
  val kineticTypography = kineticTypography()
  val m3Typography = kineticM3Typography(kineticTypography)
  val colorScheme = if (darkTheme) darkColorScheme else lightColorScheme
  CompositionLocalProvider(
    LocalKineticPalette provides palette,
    LocalKineticTypography provides kineticTypography,
  ) {
    MaterialTheme(colorScheme = colorScheme, typography = m3Typography, shapes = kineticShapes) {
      content()
    }
  }
}
