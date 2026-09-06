package proj.memorchess.axl.ui.theme

import androidx.compose.ui.graphics.Color
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.comparables.shouldBeGreaterThanOrEqualTo
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.floats.shouldBeBetween
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlin.test.Test

/** Hue angle in degrees `[0, 360)`, computed from RGB. Achromatic colors report `0`. */
private fun hueDegrees(color: Color): Float {
  val r = color.red
  val g = color.green
  val b = color.blue
  val max = maxOf(r, g, b)
  val min = minOf(r, g, b)
  val delta = max - min
  if (delta == 0f) return 0f
  val rawHue =
    when (max) {
      r -> ((g - b) / delta) % 6f
      g -> (b - r) / delta + 2f
      else -> (r - g) / delta + 4f
    } * 60f
  return if (rawHue < 0f) rawHue + 360f else rawHue
}

/** Maximum hue drift tolerated for a role that is meant to keep one hue across both themes. */
private const val MAX_CROSS_THEME_HUE_DRIFT = 12f

/** Smallest angular distance in degrees between two hues, handling the 360 wraparound. */
private fun hueDistance(a: Color, b: Color): Float {
  val raw = kotlin.math.abs(hueDegrees(a) - hueDegrees(b))
  return if (raw > 180f) 360f - raw else raw
}

/** HSL lightness in `[0, 1]`: the midpoint of the brightest and darkest RGB channel. */
private fun lightness(color: Color): Float {
  val max = maxOf(color.red, color.green, color.blue)
  val min = minOf(color.red, color.green, color.blue)
  return (max + min) / 2f
}

class TestKineticPalette {

  @Test
  fun actionIsHueStableAcrossThemes() {
    hueDistance(KineticDarkPalette.action, KineticLightPalette.action)
      .shouldBeBetween(0f, MAX_CROSS_THEME_HUE_DRIFT, 0f)
  }

  @Test
  fun streakIsHueStableAcrossThemes() {
    hueDistance(KineticDarkPalette.streak, KineticLightPalette.streak)
      .shouldBeBetween(0f, MAX_CROSS_THEME_HUE_DRIFT, 0f)
  }

  @Test
  fun destructiveIsHueStableAcrossThemes() {
    hueDistance(KineticDarkPalette.destructive, KineticLightPalette.destructive)
      .shouldBeBetween(0f, MAX_CROSS_THEME_HUE_DRIFT, 0f)
  }

  @Test
  fun progressReassignsHueBetweenThemes() {
    KineticDarkPalette.progress shouldNotBe KineticLightPalette.progress
  }

  @Test
  fun progressIsLimeInDarkAndVioletInLight() {
    hueDegrees(KineticDarkPalette.progress).shouldBeBetween(60f, 100f, 0f)
    hueDegrees(KineticLightPalette.progress).shouldBeBetween(250f, 280f, 0f)
  }

  @Test
  fun destructiveAndStreakShareOnePinkPerTheme() {
    KineticDarkPalette.destructive shouldBe KineticDarkPalette.streak
    KineticLightPalette.destructive shouldBe KineticLightPalette.streak
    hueDegrees(KineticDarkPalette.streak).shouldBeBetween(300f, 350f, 0f)
    hueDegrees(KineticLightPalette.streak).shouldBeBetween(300f, 350f, 0f)
  }

  @Test
  fun dangerBorderIsAtLeastAsVisibleAsTheNeutralBorder() {
    for (palette in listOf(KineticDarkPalette, KineticLightPalette)) {
      val dangerDelta =
        kotlin.math.abs(lightness(palette.destructiveDim) - lightness(palette.panel))
      val neutralDelta = kotlin.math.abs(lightness(palette.line) - lightness(palette.panel))
      dangerDelta.shouldBeGreaterThanOrEqualTo(neutralDelta)
    }
  }

  @Test
  fun surfaceLadderIsMonotonic() {
    lightness(KineticLightPalette.bg).shouldBeGreaterThan(lightness(KineticLightPalette.bg2))
    lightness(KineticDarkPalette.bg).shouldBeLessThan(lightness(KineticDarkPalette.panel))
  }

  @Test
  fun pressableEdgeIsVisibleAgainstTheDefaultButtonFill() {
    KineticDarkPalette.lineBright shouldNotBe KineticDarkPalette.panel2
    KineticLightPalette.lineBright shouldNotBe KineticLightPalette.panel2
  }

  @Test
  fun darkColorSchemeDerivesPrimaryFromAction() {
    darkColorScheme.primary shouldBe KineticDarkPalette.action
  }

  @Test
  fun lightColorSchemeDerivesErrorFromDestructive() {
    lightColorScheme.error shouldBe KineticLightPalette.destructive
  }

  @Test
  fun glowTokensAreFullyOpaqueInDarkPalette() {
    KineticDarkPalette.actionGlow.alpha shouldBe 1f
    KineticDarkPalette.progressGlow.alpha shouldBe 1f
    KineticDarkPalette.streakGlow.alpha shouldBe 1f
    KineticDarkPalette.destructiveGlow.alpha shouldBe 1f
  }

  @Test
  fun glowTokensAreFullyOpaqueInLightPalette() {
    KineticLightPalette.actionGlow.alpha shouldBe 1f
    KineticLightPalette.progressGlow.alpha shouldBe 1f
    KineticLightPalette.streakGlow.alpha shouldBe 1f
    KineticLightPalette.destructiveGlow.alpha shouldBe 1f
  }

  @Test
  fun streakTextDiffersFromProgressInDarkPalette() {
    KineticDarkPalette.streakText shouldNotBe KineticDarkPalette.progress
  }

  @Test
  fun streakTextDiffersFromProgressInLightPalette() {
    KineticLightPalette.streakText shouldNotBe KineticLightPalette.progress
  }

  @Test
  fun ink3DiffersFromProgressAndDestructiveInBothPalettes() {
    KineticDarkPalette.ink3 shouldNotBe KineticDarkPalette.progress
    KineticDarkPalette.ink3 shouldNotBe KineticDarkPalette.destructive
    KineticLightPalette.ink3 shouldNotBe KineticLightPalette.progress
    KineticLightPalette.ink3 shouldNotBe KineticLightPalette.destructive
  }

  @Test
  fun destructiveHueIsPinkNotRedInDarkPalette() {
    hueDegrees(KineticDarkPalette.destructive).shouldBeBetween(300f, 350f, 0f)
  }

  @Test
  fun destructiveHueIsPinkNotRedInLightPalette() {
    hueDegrees(KineticLightPalette.destructive).shouldBeBetween(300f, 350f, 0f)
  }
}
