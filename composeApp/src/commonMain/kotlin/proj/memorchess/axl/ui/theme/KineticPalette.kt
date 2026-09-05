package proj.memorchess.axl.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Canonical Kinetic design tokens: a gamified role palette of electric violet, hot pink, and lime.
 *
 * Two palette instances exist, [KineticDarkPalette] and [KineticLightPalette]. `action`, `streak`
 * and `destructive` keep the same hue in both. `progress` swaps hue per theme, using lime in dark
 * and violet in light. The light palette stays cool and futuristic with an ice white base. It is
 * never warm or cream.
 */
@Immutable
data class KineticPalette(
  // Surface ladder
  val bg: Color,
  val bg2: Color,
  val panel: Color,
  val panel2: Color,
  val panel3: Color,

  // Lines
  val line: Color,
  val lineBright: Color,

  // Ink (text) ladder
  val ink: Color,
  val ink2: Color,
  val ink3: Color,
  val ink4: Color,

  // Action: the primary interactive role. Electric violet, same hue in both themes.
  val action: Color,
  val actionText: Color,
  val actionGlow: Color,
  val actionDim: Color,
  val actionSoft: Color,
  val onAction: Color,

  // Progress: the completion and success role. Lime in dark, violet in light.
  val progress: Color,
  val progressText: Color,
  val progressGlow: Color,
  val progressDim: Color,
  val progressSoft: Color,
  val onProgress: Color,

  // Streak: the secondary highlight role. Hot pink, same hue in both themes.
  val streak: Color,
  val streakText: Color,
  val streakGlow: Color,
  val streakDim: Color,
  val streakSoft: Color,
  val onStreak: Color,

  // Destructive: the dangerous action role. Hot pink, never red, same hue in both themes.
  val destructive: Color,
  val destructiveText: Color,
  val destructiveGlow: Color,
  val destructiveDim: Color,
  val destructiveSoft: Color,
  val onDestructive: Color,

  // Board squares (Kinetic-aligned defaults)
  val sqLight: Color,
  val sqDark: Color,

  /** True when this palette describes the light theme. Used by shadow helpers and a few visuals. */
  val isLight: Boolean,
)

/** Dark Kinetic palette — primary mode of the app. */
val KineticDarkPalette =
  KineticPalette(
    bg = Color(0xFF07080A),
    bg2 = Color(0xFF0C0E12),
    panel = Color(0xFF11141A),
    panel2 = Color(0xFF161A22),
    panel3 = Color(0xFF1D222C),
    line = Color(0xFF232936),
    lineBright = Color(0xFF2F3645),
    ink = Color(0xFFF5F6F8),
    ink2 = Color(0xFFC4C8D0),
    ink3 = Color(0xFF7A8090),
    ink4 = Color(0xFF4A5060),
    action = Color(0xFF8B5CF6),
    actionText = Color(0xFF8B5CF6),
    actionGlow = Color(0xFFA78BFA),
    actionDim = Color(0xFF4C1D95),
    actionSoft = Color(0x248B5CF6), // action at 14% opacity
    onAction = Color(0xFFFFFFFF),
    progress = Color(0xFFA3E635),
    progressText = Color(0xFFA3E635),
    progressGlow = Color(0xFFD9F99D),
    progressDim = Color(0xFF3F6212),
    progressSoft = Color(0x1FA3E635), // progress at 12% opacity
    onProgress = Color(0xFF000000),
    streak = Color(0xFFFF3D9A),
    streakText = Color(0xFFFF3D9A),
    streakGlow = Color(0xFFFF7AC1),
    streakDim = Color(0xFF7A1550),
    streakSoft = Color(0x24FF3D9A), // streak at 14% opacity
    onStreak = Color(0xFFFFFFFF),
    destructive = Color(0xFFF23278),
    destructiveText = Color(0xFFF23278),
    destructiveGlow = Color(0xFFFF6FA3),
    destructiveDim = Color(0xFF7A1030),
    destructiveSoft = Color(0x1AF23278), // destructive at 10% opacity
    onDestructive = Color(0xFFFFFFFF),
    sqLight = Color(0xFFD7DDE6),
    sqDark = Color(0xFF3A4150),
    isLight = false,
  )

/** Light Kinetic palette. Futuristic and cool with an ice white base. Never warm or cream. */
val KineticLightPalette =
  KineticPalette(
    bg = Color(0xFFEEF2F7),
    bg2 = Color(0xFFE2E8EF),
    panel = Color(0xFFFFFFFF),
    panel2 = Color(0xFFF5F8FB),
    panel3 = Color(0xFFD9DFE7),
    line = Color(0xFFC2CAD6),
    lineBright = Color(0xFF8A95A8),
    ink = Color(0xFF0A0E16),
    ink2 = Color(0xFF1F2738),
    ink3 = Color(0xFF54607A),
    ink4 = Color(0xFF8693AB),
    action = Color(0xFF8B5CF6),
    actionText = Color(0xFF6D28D9),
    actionGlow = Color(0xFFA78BFA),
    actionDim = Color(0xFFE4D4FF),
    actionSoft = Color(0x1A8B5CF6), // action at 10% opacity
    onAction = Color(0xFFFFFFFF),
    progress = Color(0xFF7C3AED),
    progressText = Color(0xFF5B21B6),
    progressGlow = Color(0xFFC4B5FD),
    progressDim = Color(0xFFEEE0FB),
    progressSoft = Color(0x1A7C3AED), // progress at 10% opacity
    onProgress = Color(0xFFFFFFFF),
    streak = Color(0xFFFF3D9A),
    streakText = Color(0xFFC81870),
    streakGlow = Color(0xFFFF7AC1),
    streakDim = Color(0xFFFFD6EA),
    streakSoft = Color(0x1AFF3D9A), // streak at 10% opacity
    onStreak = Color(0xFFFFFFFF),
    destructive = Color(0xFFF23278),
    destructiveText = Color(0xFFB0184F),
    destructiveGlow = Color(0xFFFF6FA3),
    destructiveDim = Color(0xFFFDD6E4),
    destructiveSoft = Color(0x14F23278), // destructive at 8% opacity
    onDestructive = Color(0xFFFFFFFF),
    sqLight = Color(0xFFE5EDF5),
    sqDark = Color(0xFF5E6A82),
    isLight = true,
  )
