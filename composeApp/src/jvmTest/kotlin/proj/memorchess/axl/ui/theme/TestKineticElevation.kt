package proj.memorchess.axl.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlin.test.Test
import proj.memorchess.axl.ui.setKineticContent

/**
 * Verifies [kineticPressableEdgeColor] resolves to a solid, opaque edge in both palettes, and that
 * [kineticShadow]'s `shape`/`drawBorder` parameters behave as documented: a default (no `shape`
 * argument) call still reaches the exact old square offset, an explicit rounded [CircleShape]
 * genuinely reshapes the drawn outline, and `drawBorder = false` suppresses the modifier's own line
 * stroke.
 */
@OptIn(ExperimentalTestApi::class)
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

  /**
   * [kineticShadow]'s offset block is drawn *outside* the shadowed node's own bounds, so the parent
   * here reserves 10.dp of room (mirroring
   * [proj.memorchess.axl.ui.components.popup.KineticDialog]'s own `SHADOW_ROOM` pattern) — the
   * parent, tagged [tag], is what gets captured, since capturing the child itself would clip the
   * very overflow these tests are about.
   */
  @Composable
  private fun ReservedRoomBox(tag: String, shape: Shape?) {
    Box(
      modifier =
        Modifier.testTag(tag).background(Color(0xFF000000)).padding(end = 10.dp, bottom = 10.dp)
    ) {
      Box(
        modifier =
          Modifier.size(40.dp)
            .let {
              if (shape == null) it.kineticShadow(big = false, drawBorder = false)
              else it.kineticShadow(big = false, shape = shape, drawBorder = false)
            }
            .background(Color.White)
      )
    }
  }

  @Test
  fun roundedShapeClipsTheOffsetCorner() = runComposeUiTest {
    setKineticContent { ReservedRoomBox(tag = SHADOW_PARENT_TAG, shape = CircleShape) }
    // Sampled past the shadowed child's own bottom-right corner but still inside the parent's
    // reserved room. A rounded shape's offset block never reaches this point, so the pixel here
    // should still read the untouched background exactly.
    val corner = with(density) { 40.dp.roundToPx() } + 4
    val pixel = onNodeWithTag(SHADOW_PARENT_TAG).captureToImage().toPixelMap()[corner, corner]
    pixel shouldBe Color(0xFF000000)
  }

  /**
   * A default (no `shape` argument) call still reaches the exact old square offset — the same
   * corner point a rounded [CircleShape] clips out of, sampled from two siblings rendered in the
   * same composition. Comparing the two rendered pixels directly, rather than asserting the default
   * one merely differs from a hardcoded pure-black constant, does not depend on the 0.08-alpha
   * shadow-over-black blend rounding to a channel value 8-bit precision can still tell apart from
   * `0xFF000000`.
   */
  @Test
  fun defaultShapeShadowDiffersFromRoundedShapeAtTheOffsetCorner() = runComposeUiTest {
    setKineticContent {
      Column {
        ReservedRoomBox(tag = "defaultShape", shape = null)
        ReservedRoomBox(tag = "roundedShape", shape = CircleShape)
      }
    }
    val corner = with(density) { 40.dp.roundToPx() } + 4
    val defaultPixel = onNodeWithTag("defaultShape").captureToImage().toPixelMap()[corner, corner]
    val roundedPixel = onNodeWithTag("roundedShape").captureToImage().toPixelMap()[corner, corner]
    defaultPixel shouldNotBe roundedPixel
  }

  @Test
  fun drawBorderFalseOmitsTheLineStroke() = runComposeUiTest {
    setKineticContent {
      Column {
        Box(
          modifier =
            Modifier.size(40.dp)
              .testTag("withBorder")
              .background(Color.White)
              .kineticShadow(big = false)
        )
        Box(
          modifier =
            Modifier.size(40.dp)
              .testTag("noBorder")
              .background(Color.White)
              .kineticShadow(big = false, drawBorder = false)
        )
      }
    }
    val edgeY = with(density) { 20.dp.roundToPx() }
    val withBorderPixel = onNodeWithTag("withBorder").captureToImage().toPixelMap()[0, edgeY]
    val noBorderPixel = onNodeWithTag("noBorder").captureToImage().toPixelMap()[0, edgeY]
    withBorderPixel shouldBe KineticLightPalette.line
    noBorderPixel shouldNotBe KineticLightPalette.line
  }
}

private const val SHADOW_PARENT_TAG = "shadowParent"
