package proj.memorchess.axl.ui.theme

import androidx.compose.ui.graphics.Color
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

class TestKineticPalette {

  @Test
  fun actionIsHueStableAcrossThemes() {
    KineticDarkPalette.action shouldBe KineticLightPalette.action
  }

  @Test
  fun streakIsHueStableAcrossThemes() {
    KineticDarkPalette.streak shouldBe KineticLightPalette.streak
  }

  @Test
  fun destructiveIsHueStableAcrossThemes() {
    KineticDarkPalette.destructive shouldBe KineticLightPalette.destructive
  }

  @Test
  fun progressReassignsHueBetweenThemes() {
    KineticDarkPalette.progress shouldNotBe KineticLightPalette.progress
  }

  @Test
  fun lightProgressDiffersFromLightAction() {
    KineticLightPalette.progress shouldNotBe KineticLightPalette.action
  }

  @Test
  fun destructiveDiffersFromStreak() {
    KineticDarkPalette.destructive shouldNotBe KineticDarkPalette.streak
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
