package proj.memorchess.axl.ui.theme

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlin.test.Test

/** Verifies [kineticPressableEdgeColor] resolves to a solid, opaque edge in both palettes. */
internal class TestKineticElevation {

  @Test
  fun edgeColorIsFullyOpaqueInLightPalette() {
    kineticPressableEdgeColor(KineticLightPalette).alpha shouldBe 1f
  }

  @Test
  fun edgeColorIsFullyOpaqueInDarkPalette() {
    kineticPressableEdgeColor(KineticDarkPalette).alpha shouldBe 1f
  }

  @Test
  fun edgeColorMatchesTheBrightLineTokenInLightPalette() {
    kineticPressableEdgeColor(KineticLightPalette) shouldBe KineticLightPalette.lineBright
  }

  @Test
  fun edgeColorMatchesTheBrightLineTokenInDarkPalette() {
    kineticPressableEdgeColor(KineticDarkPalette) shouldBe KineticDarkPalette.lineBright
  }

  @Test
  fun edgeColorIsVisibleAgainstTheDefaultButtonFill() {
    kineticPressableEdgeColor(KineticLightPalette) shouldNotBe KineticLightPalette.panel2
    kineticPressableEdgeColor(KineticDarkPalette) shouldNotBe KineticDarkPalette.panel2
  }

  @Test
  fun edgeColorIsVisibleAgainstThePageBackground() {
    kineticPressableEdgeColor(KineticLightPalette) shouldNotBe KineticLightPalette.bg
    kineticPressableEdgeColor(KineticDarkPalette) shouldNotBe KineticDarkPalette.bg
  }
}
