package proj.memorchess.axl.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme

/**
 * Maps a [KineticPalette] onto a Material 3 [ColorScheme] so M3 widgets that haven't been migrated
 * to bespoke Kinetic Composables still pick up the right colors.
 *
 * Mapping (from Kinetic token → M3 role):
 * - `accent` → `primary`
 * - `cyan` → `secondary` and `tertiary` (the cool tech accent)
 * - `bg` → `background`, `panel`/`panel2`/`panel3` → `surface` family
 * - `line` → `outline`, `lineBright` → `outlineVariant`
 * - `ink` → `onSurface`, `ink2` → `onSurfaceVariant`
 * - `red` → `error`
 */
internal fun kineticToM3(palette: KineticPalette): ColorScheme {
  val base = if (palette.isLight) lightColorScheme() else darkColorScheme()
  return base.copy(
    primary = palette.accent,
    onPrimary = palette.onAccent,
    primaryContainer = palette.accentDim,
    onPrimaryContainer = if (palette.isLight) palette.accentText else palette.accentGlow,
    inversePrimary = palette.accentGlow,
    secondary = palette.cyan,
    onSecondary = palette.onAccent,
    secondaryContainer = palette.panel2,
    onSecondaryContainer = palette.ink2,
    tertiary = palette.cyan,
    onTertiary = palette.onAccent,
    tertiaryContainer = palette.panel3,
    onTertiaryContainer = palette.ink2,
    background = palette.bg,
    onBackground = palette.ink,
    surface = palette.panel,
    onSurface = palette.ink,
    surfaceVariant = palette.panel2,
    onSurfaceVariant = palette.ink2,
    surfaceTint = palette.accent,
    inverseSurface = palette.ink,
    inverseOnSurface = palette.bg,
    error = palette.red,
    onError = palette.onAccent,
    errorContainer = palette.redDim,
    onErrorContainer = palette.red,
    outline = palette.line,
    outlineVariant = palette.lineBright,
    scrim = palette.bg2,
    surfaceBright = palette.panel2,
    surfaceDim = palette.bg2,
    surfaceContainer = palette.panel,
    surfaceContainerHigh = palette.panel2,
    surfaceContainerHighest = palette.panel3,
    surfaceContainerLow = palette.bg2,
    surfaceContainerLowest = palette.bg,
  )
}

/** Light Material 3 color scheme derived from [KineticLightPalette]. */
val lightColorScheme: ColorScheme = kineticToM3(KineticLightPalette)

/** Dark Material 3 color scheme derived from [KineticDarkPalette]. */
val darkColorScheme: ColorScheme = kineticToM3(KineticDarkPalette)
