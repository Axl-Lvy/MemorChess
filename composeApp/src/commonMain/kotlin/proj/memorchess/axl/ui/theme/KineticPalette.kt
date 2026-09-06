package proj.memorchess.axl.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Canonical Kinetic design tokens: a gamified role palette of electric violet, hot pink, and lime.
 *
 * Two palette instances exist, [KineticDarkPalette] and [KineticLightPalette]. `action`, `streak`
 * and `destructive` keep the same hue in both. `progress` swaps hue per theme, using lime in dark
 * and violet in light. The light palette is violet-tinted throughout: a soft lavender-white base
 * under white panels, with violet-grey ink and violet-tinted lines. It is never warm, cream, or
 * neutral grey.
 *
 * Two role collisions are deliberate, taken straight from the mockup rather than worked around:
 * - In light, `progress` shares the `action` violet. The two roles separate by hue only in dark,
 *   where `progress` is lime.
 * - `streak` and `destructive` share one pink per theme (`#D6106B` light, `#FF4FA3` dark) and are
 *   told apart by context, not by base hue.
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
  /**
   * Border tone of the streak container, mirroring [destructiveDim]. In light it is the same soft
   * pink as [destructiveDim] — the light artboards hold exactly one soft pink container tone.
   */
  val streakDim: Color,
  /**
   * Hard bottom edge of a streak container — the mockup's `box-shadow: 0 3px 0`. The only token in
   * the palette that is deliberately DARKER than its own fill in both themes; no existing token
   * qualifies ([streakText] equals [streak] in dark, and [lineBright] is a lavender grey that reads
   * as a highlight on pink).
   */
  val streakEdge: Color,
  val streakSoft: Color,
  val onStreak: Color,

  // Destructive: the dangerous action role. Hot pink, never red, same hue in both themes.
  val destructive: Color,
  val destructiveText: Color,
  val destructiveGlow: Color,
  /**
   * Border tone of the danger container, **not** its fill — the fill is [destructiveSoft]. Two of
   * its consumers (the `DangerOutline` button style and a `danger = true` settings section) stroke
   * it straight onto a panel, so it must stay at least as visible against [panel] as [line] is.
   */
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
    bg = Color(0xFF120A22),
    bg2 = Color(0xFF1A1030),
    panel = Color(0xFF1E1338),
    panel2 = Color(0xFF271A45),
    panel3 = Color(0xFF2E1D52),
    line = Color(0xFF2E1D52),
    lineBright = Color(0xFF392658),
    ink = Color(0xFFF8F4FF),
    ink2 = Color(0xFFC9BCE8),
    ink3 = Color(0xFF8B7BB0),
    ink4 = Color(0xFF6F6191),
    action = Color(0xFFB99BFF),
    actionText = Color(0xFFB99BFF),
    actionGlow = Color(0xFFDCD0FF),
    actionDim = Color(0xFF3E2A6B),
    actionSoft = Color(0x24B99BFF), // action at 14% opacity
    onAction = Color(0xFF180A2E),
    progress = Color(0xFFB4F542),
    progressText = Color(0xFFB4F542),
    progressGlow = Color(0xFFD6F5A8),
    progressDim = Color(0xFF1F2E0A),
    progressSoft = Color(0x1FB4F542), // progress at 12% opacity
    onProgress = Color(0xFF24350A),
    streak = Color(0xFFFF4FA3),
    streakText = Color(0xFFFF4FA3),
    streakGlow = Color(0xFFFFD1E6),
    streakDim = Color(0xFF4A0F2C),
    streakEdge = Color(0xFFB01463),
    streakSoft = Color(0x24FF4FA3), // streak at 14% opacity
    onStreak = Color(0xFF2A0A1B),
    destructive = Color(0xFFFF4FA3),
    destructiveText = Color(0xFFFF4FA3),
    destructiveGlow = Color(0xFFF0A8CC),
    destructiveDim = Color(0xFF7A2555),
    destructiveSoft = Color(0x1AFF4FA3), // destructive at 10% opacity
    onDestructive = Color(0xFF2A0A1B),
    sqLight = Color(0xFFD7DDE6),
    sqDark = Color(0xFF3A4150),
    isLight = false,
  )

/**
 * Light Kinetic palette. Violet-tinted throughout: a soft lavender-white base under white panels,
 * with violet-grey ink and violet-tinted lines. Never warm, cream, or neutral grey.
 */
val KineticLightPalette =
  KineticPalette(
    bg = Color(0xFFF4F0FF),
    // The one light token with no artboard of its own: bg2 is the mockup's canvas chrome, never a
    // screen surface, so it is set by interpolation between bg #F4F0FF and panel3 #EDE4FF.
    bg2 = Color(0xFFEEEAF6),
    panel = Color(0xFFFFFFFF),
    panel2 = Color(0xFFF4F0FF),
    panel3 = Color(0xFFEDE4FF),
    line = Color(0xFFE4DAFB),
    lineBright = Color(0xFFC9BCE8),
    ink = Color(0xFF1A1030),
    ink2 = Color(0xFF4A3A6B),
    ink3 = Color(0xFF6F6191),
    ink4 = Color(0xFF8B7BB0),
    action = Color(0xFF6D28D9),
    actionText = Color(0xFF6D28D9),
    actionGlow = Color(0xFFB99BFF),
    actionDim = Color(0xFFEDE4FF),
    actionSoft = Color(0x1A6D28D9), // action at 10% opacity
    onAction = Color(0xFFFFFFFF),
    progress = Color(0xFF6D28D9),
    progressText = Color(0xFF5B21B6),
    progressGlow = Color(0xFFB99BFF),
    progressDim = Color(0xFFEDE4FF),
    progressSoft = Color(0x1A6D28D9), // progress at 10% opacity
    onProgress = Color(0xFFFFFFFF),
    streak = Color(0xFFD6106B),
    streakText = Color(0xFFB01463),
    streakGlow = Color(0xFFFF4FA3),
    streakDim = Color(0xFFFFD1E6),
    streakEdge = Color(0xFFA10D50),
    streakSoft = Color(0x1AD6106B), // streak at 10% opacity
    onStreak = Color(0xFFFFFFFF),
    destructive = Color(0xFFD6106B),
    destructiveText = Color(0xFFB01463),
    destructiveGlow = Color(0xFFFF4FA3),
    destructiveDim = Color(0xFFFFD1E6),
    destructiveSoft = Color(0x14D6106B), // destructive at 8% opacity
    onDestructive = Color(0xFFFFFFFF),
    sqLight = Color(0xFFE5EDF5),
    sqDark = Color(0xFF5E6A82),
    isLight = true,
  )
