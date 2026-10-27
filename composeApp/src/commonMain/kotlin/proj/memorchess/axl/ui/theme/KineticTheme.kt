package proj.memorchess.axl.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import proj.memorchess.axl.ui.components.board.LocalPiecePainters
import proj.memorchess.axl.ui.components.board.rememberPiecePainters

/** Exposes the active [KineticPalette]; falls back to the dark palette outside [KineticTheme]. */
val LocalKineticPalette = staticCompositionLocalOf { KineticDarkPalette }

/** Provides the Kinetic palette, typography and a matching Material 3 theme to [content]. */
@Composable
fun KineticTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
  val palette = if (darkTheme) KineticDarkPalette else KineticLightPalette
  val kineticTypography = kineticTypography()
  val m3Typography = kineticM3Typography(kineticTypography)
  val colorScheme = if (darkTheme) darkColorScheme else lightColorScheme
  val piecePainters = rememberPiecePainters()
  CompositionLocalProvider(
    LocalKineticPalette provides palette,
    LocalKineticTypography provides kineticTypography,
    LocalPiecePainters provides piecePainters,
  ) {
    MaterialTheme(colorScheme = colorScheme, typography = m3Typography, shapes = kineticShapes) {
      content()
    }
  }
}
