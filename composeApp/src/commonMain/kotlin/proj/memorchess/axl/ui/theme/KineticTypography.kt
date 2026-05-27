package proj.memorchess.axl.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import memorchess.composeapp.generated.resources.BricolageGrotesque_Bold
import memorchess.composeapp.generated.resources.BricolageGrotesque_ExtraBold
import memorchess.composeapp.generated.resources.InterTight_Medium
import memorchess.composeapp.generated.resources.InterTight_Regular
import memorchess.composeapp.generated.resources.InterTight_SemiBold
import memorchess.composeapp.generated.resources.JetBrainsMono_Medium
import memorchess.composeapp.generated.resources.JetBrainsMono_Regular
import memorchess.composeapp.generated.resources.Res
import org.jetbrains.compose.resources.Font

/** Bricolage Grotesque — Kinetic display font (brand, headings, pill values, slider readout). */
@Composable
private fun bricolageFamily(): FontFamily =
  FontFamily(
    Font(Res.font.BricolageGrotesque_Bold, weight = FontWeight.Bold),
    Font(Res.font.BricolageGrotesque_ExtraBold, weight = FontWeight.ExtraBold),
  )

/** Inter Tight — Kinetic body font. */
@Composable
private fun interTightFamily(): FontFamily =
  FontFamily(
    Font(Res.font.InterTight_Regular, weight = FontWeight.Normal),
    Font(Res.font.InterTight_Medium, weight = FontWeight.Medium),
    Font(Res.font.InterTight_SemiBold, weight = FontWeight.SemiBold),
  )

/** JetBrains Mono — Kinetic mono font for notation, stats, counters, labels. */
@Composable
private fun jetbrainsMonoFamily(): FontFamily =
  FontFamily(
    Font(Res.font.JetBrainsMono_Regular, weight = FontWeight.Normal),
    Font(Res.font.JetBrainsMono_Medium, weight = FontWeight.Medium),
  )

/**
 * Kinetic-specific text styles that don't fit cleanly into Material 3 type roles.
 *
 * The M3 [Typography] returned by [kineticM3Typography] covers Material widgets; this carries the
 * Kinetic-only roles used by custom Composables (brand mark, slider readouts, pill labels, etc.).
 */
@Immutable
data class KineticTypography(
  /** Brand wordmark — Bricolage 700 20sp -0.03em. */
  val brand: TextStyle,
  /** Section heading — Bricolage 800 24sp -0.03em. */
  val displayLg: TextStyle,
  /** Inline display heading — Bricolage 700 16sp -0.02em. */
  val display: TextStyle,
  /** Small display label — Bricolage 700 12sp. */
  val displaySm: TextStyle,
  /** Body — Inter Tight 500 13sp, 1.45 line-height. */
  val body: TextStyle,
  /** Small body — Inter Tight 500 11sp. */
  val bodySm: TextStyle,
  /** Mono — JetBrains Mono 500 11sp, 0.02em tracking. */
  val mono: TextStyle,
  /** Small uppercase mono — JetBrains Mono 500 9sp, 0.1em tracking. */
  val monoSm: TextStyle,
)

/** Builds the Kinetic typography set. Must be called inside a Composable scope. */
@Composable
internal fun kineticTypography(): KineticTypography {
  val display = bricolageFamily()
  val body = interTightFamily()
  val mono = jetbrainsMonoFamily()
  return KineticTypography(
    brand =
      TextStyle(
        fontFamily = display,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        letterSpacing = (-0.03).em,
      ),
    displayLg =
      TextStyle(
        fontFamily = display,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 24.sp,
        letterSpacing = (-0.03).em,
      ),
    display =
      TextStyle(
        fontFamily = display,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        letterSpacing = (-0.02).em,
      ),
    displaySm = TextStyle(fontFamily = display, fontWeight = FontWeight.Bold, fontSize = 12.sp),
    body =
      TextStyle(
        fontFamily = body,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.85.sp, // 13 * 1.45
      ),
    bodySm = TextStyle(fontFamily = body, fontWeight = FontWeight.Medium, fontSize = 11.sp),
    mono =
      TextStyle(
        fontFamily = mono,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        letterSpacing = 0.02.em,
      ),
    monoSm =
      TextStyle(
        fontFamily = mono,
        fontWeight = FontWeight.Medium,
        fontSize = 9.sp,
        letterSpacing = 0.1.em,
      ),
  )
}

/**
 * Derives a Material 3 [Typography] from the Kinetic styles so M3 widgets pick up Kinetic fonts
 * even before they are migrated to bespoke Composables.
 */
internal fun kineticM3Typography(kinetic: KineticTypography): Typography =
  Typography(
    displayLarge = kinetic.displayLg,
    displayMedium = kinetic.display,
    displaySmall = kinetic.displaySm,
    headlineLarge = kinetic.displayLg,
    headlineMedium = kinetic.display,
    headlineSmall = kinetic.displaySm,
    titleLarge = kinetic.display,
    titleMedium = kinetic.display.copy(fontSize = 14.sp),
    titleSmall = kinetic.bodySm.copy(fontWeight = FontWeight.SemiBold),
    bodyLarge = kinetic.body.copy(fontSize = 14.sp),
    bodyMedium = kinetic.body,
    bodySmall = kinetic.bodySm,
    labelLarge = kinetic.mono,
    labelMedium = kinetic.monoSm.copy(fontSize = 10.sp),
    labelSmall = kinetic.monoSm,
  )

/** CompositionLocal exposing Kinetic-only text styles to consumers. */
val LocalKineticTypography =
  staticCompositionLocalOf<KineticTypography> {
    error("LocalKineticTypography not provided. Wrap your UI in AppTheme { … }.")
  }
