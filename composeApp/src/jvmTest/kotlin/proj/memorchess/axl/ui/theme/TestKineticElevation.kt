package proj.memorchess.axl.ui.theme

import io.kotest.matchers.shouldBe
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
}
