package proj.memorchess.axl.ui.theme

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlin.test.Test

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
}
