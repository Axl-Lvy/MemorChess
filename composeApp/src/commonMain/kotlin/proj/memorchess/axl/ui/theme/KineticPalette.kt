package proj.memorchess.axl.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Canonical Kinetic design tokens.
 *
 * Values mirror `design-proposals/kinetic-base.css` exactly. Two palette instances exist —
 * [KineticDarkPalette] and [KineticLightPalette]. The light palette is deliberately cool/futuristic
 * (ice white + electric cyan secondary), not warm/cream.
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

  // Accent (orange)
  val accent: Color,
  val accentText: Color,
  val accentGlow: Color,
  val accentDim: Color,
  val accentSoft: Color,
  val onAccent: Color,

  // Cyan (secondary tech accent)
  val cyan: Color,
  val cyanGlow: Color,

  // Semantic
  val green: Color,
  val greenDim: Color,
  val greenSoft: Color,
  val red: Color,
  val redDim: Color,
  val redSoft: Color,

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
    accent = Color(0xFFFF5B26),
    accentText = Color(0xFFFF5B26),
    accentGlow = Color(0xFFFF7A4D),
    accentDim = Color(0xFF993515),
    accentSoft = Color(0x24FF5B26), // accent at 14% opacity
    onAccent = Color(0xFF000000),
    cyan = Color(0xFF4CD6E8),
    cyanGlow = Color(0x664CD6E8), // cyan at 40% opacity
    green = Color(0xFF6EE7A4),
    greenDim = Color(0xFF2A6B3E),
    greenSoft = Color(0x1F6EE7A4), // green at 12% opacity
    red = Color(0xFFFF4060),
    redDim = Color(0xFF6B2030),
    redSoft = Color(0x1AFF4060), // red at 10% opacity
    sqLight = Color(0xFFD7DDE6),
    sqDark = Color(0xFF3A4150),
    isLight = false,
  )

/** Light Kinetic palette — futuristic, cool ice white + electric cyan, never warm/cream. */
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
    accent = Color(0xFFFF5B26),
    accentText = Color(0xFFD63D0C),
    accentGlow = Color(0xFFFF7847),
    accentDim = Color(0xFFFFD8C8),
    accentSoft = Color(0x1AFF5B26), // accent at 10% opacity
    onAccent = Color(0xFFFFFFFF),
    cyan = Color(0xFF00B8D4),
    cyanGlow = Color(0x6600B8D4), // cyan at 40% opacity
    green = Color(0xFF00A86B),
    greenDim = Color(0xFFB5E6D3),
    greenSoft = Color(0x1A00A86B), // green at 10% opacity
    red = Color(0xFFE11D48),
    redDim = Color(0xFFF4C2CC),
    redSoft = Color(0x14E11D48), // red at 8% opacity
    sqLight = Color(0xFFE5EDF5),
    sqDark = Color(0xFF5E6A82),
    isLight = true,
  )
