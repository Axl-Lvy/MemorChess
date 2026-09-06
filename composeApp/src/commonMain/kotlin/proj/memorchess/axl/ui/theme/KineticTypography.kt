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
import memorchess.composeapp.generated.resources.Baloo2_Bold
import memorchess.composeapp.generated.resources.Baloo2_ExtraBold
import memorchess.composeapp.generated.resources.Baloo2_Medium
import memorchess.composeapp.generated.resources.Baloo2_SemiBold
import memorchess.composeapp.generated.resources.JetBrainsMono_Medium
import memorchess.composeapp.generated.resources.JetBrainsMono_Regular
import memorchess.composeapp.generated.resources.Nunito_Black
import memorchess.composeapp.generated.resources.Nunito_Bold
import memorchess.composeapp.generated.resources.Nunito_ExtraBold
import memorchess.composeapp.generated.resources.Nunito_Regular
import memorchess.composeapp.generated.resources.Nunito_SemiBold
import memorchess.composeapp.generated.resources.Res
import org.jetbrains.compose.resources.Font

/** Baloo 2 — Kinetic display font (brand, headings, pill values, slider readout). */
@Composable
private fun balooFamily(): FontFamily =
  FontFamily(
    Font(Res.font.Baloo2_Medium, weight = FontWeight.Medium),
    Font(Res.font.Baloo2_SemiBold, weight = FontWeight.SemiBold),
    Font(Res.font.Baloo2_Bold, weight = FontWeight.Bold),
    Font(Res.font.Baloo2_ExtraBold, weight = FontWeight.ExtraBold),
  )

/**
 * Nunito — Kinetic body font.
 *
 * ExtraBold (800) and Black (900) are registered even though no [KineticTypography] role defaults
 * to them: the nav labels ask for those weights per-call, and without a matching face Compose would
 * resolve both down to Bold (700) and synthesise nothing (synthesis only fakes bold below W600), so
 * the active/inactive weight contrast would silently vanish.
 */
@Composable
private fun nunitoFamily(): FontFamily =
  FontFamily(
    Font(Res.font.Nunito_Regular, weight = FontWeight.Normal),
    Font(Res.font.Nunito_SemiBold, weight = FontWeight.SemiBold),
    Font(Res.font.Nunito_Bold, weight = FontWeight.Bold),
    Font(Res.font.Nunito_ExtraBold, weight = FontWeight.ExtraBold),
    Font(Res.font.Nunito_Black, weight = FontWeight.Black),
  )

/** JetBrains Mono — Kinetic mono font for chess notation. */
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
  /** Brand wordmark — Baloo 2 700 20sp -0.03em. */
  val brand: TextStyle,
  /** Section heading — Baloo 2 800 24sp -0.03em. */
  val displayLg: TextStyle,
  /** Inline display heading — Baloo 2 700 16sp -0.02em. */
  val display: TextStyle,
  /** Small display label — Baloo 2 700 12sp. */
  val displaySm: TextStyle,
  /** Body — Nunito 400 13sp, 1.45 line-height. */
  val body: TextStyle,
  /** Small body — Nunito 400 11sp. */
  val bodySm: TextStyle,
  /** Label — Nunito 700 11sp, 0.02em tracking. */
  val label: TextStyle,
  /** Small uppercase label — Nunito 700 9sp, 0.1em tracking. */
  val labelSm: TextStyle,
  /** Mono — JetBrains Mono 500 11sp, 0.02em tracking. Chess notation only. */
  val mono: TextStyle,
  /** Small uppercase mono — JetBrains Mono 500 9sp, 0.1em tracking. Chess notation only. */
  val monoSm: TextStyle,
)

/** Builds the Kinetic typography set. Must be called inside a Composable scope. */
@Composable
internal fun kineticTypography(): KineticTypography {
  val display = balooFamily()
  val body = nunitoFamily()
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
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.85.sp, // 13 * 1.45
      ),
    bodySm = TextStyle(fontFamily = body, fontWeight = FontWeight.Normal, fontSize = 11.sp),
    label =
      TextStyle(
        fontFamily = body,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        letterSpacing = 0.02.em,
      ),
    labelSm =
      TextStyle(
        fontFamily = body,
        fontWeight = FontWeight.Bold,
        fontSize = 9.sp,
        letterSpacing = 0.1.em,
      ),
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
    labelLarge = kinetic.label,
    labelMedium = kinetic.labelSm.copy(fontSize = 10.sp),
    labelSmall = kinetic.labelSm,
  )

/** CompositionLocal exposing Kinetic-only text styles to consumers. */
val LocalKineticTypography =
  staticCompositionLocalOf<KineticTypography> {
    error("LocalKineticTypography not provided. Wrap your UI in AppTheme { … }.")
  }
