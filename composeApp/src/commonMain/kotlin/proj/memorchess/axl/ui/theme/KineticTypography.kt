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

/** Kinetic display font. */
@Composable
private fun balooFamily(): FontFamily =
  FontFamily(
    Font(Res.font.Baloo2_Medium, weight = FontWeight.Medium),
    Font(Res.font.Baloo2_SemiBold, weight = FontWeight.SemiBold),
    Font(Res.font.Baloo2_Bold, weight = FontWeight.Bold),
    Font(Res.font.Baloo2_ExtraBold, weight = FontWeight.ExtraBold),
  )

/**
 * Kinetic body font. ExtraBold and Black are registered for weights requested per call: without a
 * matching face Compose resolves both to Bold and synthesises nothing.
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

/** Kinetic mono font for chess notation. */
@Composable
private fun jetbrainsMonoFamily(): FontFamily =
  FontFamily(
    Font(Res.font.JetBrainsMono_Regular, weight = FontWeight.Normal),
    Font(Res.font.JetBrainsMono_Medium, weight = FontWeight.Medium),
  )

/** Kinetic-only text styles that Material 3 type roles do not cover. */
@Immutable
data class KineticTypography(
  /** Brand wordmark. */
  val brand: TextStyle,
  /** Section heading. */
  val displayLg: TextStyle,
  /** Inline display heading. */
  val display: TextStyle,
  /** Small display label. */
  val displaySm: TextStyle,
  /** Body text. */
  val body: TextStyle,
  /** Small body text. */
  val bodySm: TextStyle,
  /** Label text. */
  val label: TextStyle,
  /** Small uppercase label. */
  val labelSm: TextStyle,
  /** Chess notation only. */
  val mono: TextStyle,
  /** Small uppercase chess notation only. */
  val monoSm: TextStyle,
)

/** Builds the Kinetic typography set. */
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

/** Derives a Material 3 [Typography] from the Kinetic styles. */
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

/** The distinct custom [FontFamily]s this typography's styles draw from. */
fun KineticTypography.fontFamilies(): List<FontFamily> =
  listOfNotNull(brand.fontFamily, body.fontFamily, mono.fontFamily).distinct()

/** Exposes the active [KineticTypography]. */
val LocalKineticTypography =
  staticCompositionLocalOf<KineticTypography> {
    error("LocalKineticTypography not provided. Wrap your UI in AppTheme { … }.")
  }
