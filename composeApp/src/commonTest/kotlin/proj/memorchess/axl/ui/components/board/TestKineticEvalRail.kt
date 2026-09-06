package proj.memorchess.axl.ui.components.board

import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.kotest.matchers.floats.shouldBeGreaterThanOrEqual
import io.kotest.matchers.floats.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlin.test.Test
import proj.memorchess.axl.ui.setKineticContent
import proj.memorchess.axl.ui.theme.KineticDarkPalette
import proj.memorchess.axl.ui.theme.KineticLightPalette

/**
 * Pins [KineticEvalRail]'s two production seams: [evalRailSafeRatio] (the clamping contract) and
 * [evalRailMarkerBand] (the marker never escapes the rail, and a zero-height rail does not throw),
 * plus [kineticEvalMarkerColor]'s palette role.
 *
 * Groups 1-3 are plain non-composable assertions and carry the whole contract on purpose: the
 * composable smoke tests of group 4 only prove that composition completes, because the draw pass
 * that `drawBehind` lives in is not guaranteed to run on every test host.
 */
@OptIn(ExperimentalTestApi::class)
internal class TestKineticEvalRail {

  // GROUP 1 — evalRailSafeRatio clamping.

  @Test
  fun safeRatioKeepsZero() {
    evalRailSafeRatio(0f) shouldBe 0f
  }

  @Test
  fun safeRatioKeepsSmallestPositiveValue() {
    evalRailSafeRatio(Float.MIN_VALUE) shouldBe Float.MIN_VALUE
  }

  @Test
  fun safeRatioKeepsHalf() {
    evalRailSafeRatio(0.5f) shouldBe 0.5f
  }

  @Test
  fun safeRatioKeepsOne() {
    evalRailSafeRatio(1f) shouldBe 1f
  }

  @Test
  fun safeRatioClampsAboveOne() {
    evalRailSafeRatio(1.5f) shouldBe 1f
  }

  @Test
  fun safeRatioClampsBelowZero() {
    evalRailSafeRatio(-0.5f) shouldBe 0f
  }

  @Test
  fun safeRatioFallsBackToHalfOnNan() {
    evalRailSafeRatio(Float.NaN) shouldBe 0.5f
  }

  @Test
  fun safeRatioFallsBackToHalfOnPositiveInfinity() {
    evalRailSafeRatio(Float.POSITIVE_INFINITY) shouldBe 0.5f
  }

  @Test
  fun safeRatioFallsBackToHalfOnNegativeInfinity() {
    evalRailSafeRatio(Float.NEGATIVE_INFINITY) shouldBe 0.5f
  }

  @Test
  fun safeRatioNormalisesNegativeZeroToPositiveZero() {
    evalRailSafeRatio(-0.0f) shouldBe 0f
  }

  @Test
  fun safeRatioReturnsCanonicalPositiveZero() {
    // Boxed equality distinguishes -0.0f from 0.0f, so pin the bits rather than trust a matcher.
    evalRailSafeRatio(-0.0f).toRawBits() shouldBe 0.0f.toRawBits()
  }

  // GROUP 2 — evalRailMarkerBand geometry.

  @Test
  fun markerBandAlwaysStaysInsideTheRail() {
    val ratios = listOf(0f, Float.MIN_VALUE, 0.25f, 0.5f, 1f)
    val heights = listOf(0f, 1f, 2f, 3f, 100f, 5000f)
    ratios.forEach { ratio ->
      heights.forEach { height ->
        val band = evalRailMarkerBand(height, MARKER_THICKNESS, ratio)
        band.start shouldBeGreaterThanOrEqual 0f
        band.endInclusive shouldBeLessThanOrEqual height
        band.start shouldBeLessThanOrEqual band.endInclusive
      }
    }
  }

  @Test
  fun markerBandIsEmptyRangeAtZeroHeight() {
    val band = evalRailMarkerBand(0f, MARKER_THICKNESS, 0.5f)
    band.start shouldBe 0f
    band.endInclusive shouldBe 0f
  }

  @Test
  fun markerBandFitsWhenRailIsThinnerThanTheMarker() {
    val band = evalRailMarkerBand(1f, MARKER_THICKNESS, 0.5f)
    band.start shouldBe 0f
    band.endInclusive shouldBe 1f
  }

  @Test
  fun markerBandTouchesTheTopEdgeAtRatioZero() {
    evalRailMarkerBand(100f, MARKER_THICKNESS, 0f).start shouldBe 0f
  }

  @Test
  fun markerBandTouchesTheBottomEdgeAtRatioOne() {
    evalRailMarkerBand(100f, MARKER_THICKNESS, 1f).endInclusive shouldBe 100f
  }

  @Test
  fun markerBandIsCentredAtHalf() {
    val band = evalRailMarkerBand(100f, MARKER_THICKNESS, 0.5f)
    band.start shouldBe 49f
    band.endInclusive shouldBe 51f
  }

  @Test
  fun markerBandTreatsNegativeZeroLikeZero() {
    val band = evalRailMarkerBand(100f, MARKER_THICKNESS, evalRailSafeRatio(-0.0f))
    band.start shouldBe 0f
    band.endInclusive shouldBe MARKER_THICKNESS
  }

  // GROUP 3 — marker colour role.

  @Test
  fun markerUsesProgressInDarkPalette() {
    kineticEvalMarkerColor(KineticDarkPalette) shouldBe KineticDarkPalette.progress
  }

  @Test
  fun markerIsLimeInDarkPalette() {
    kineticEvalMarkerColor(KineticDarkPalette) shouldBe Color(0xFFB4F542)
  }

  @Test
  fun markerLeavesTheActionRoleInDarkPalette() {
    kineticEvalMarkerColor(KineticDarkPalette) shouldNotBe KineticDarkPalette.action
  }

  @Test
  fun markerUsesProgressInLightPalette() {
    // In light, `progress` deliberately coincides with `action`; the roles split only in dark.
    kineticEvalMarkerColor(KineticLightPalette) shouldBe KineticLightPalette.progress
  }

  // GROUP 4 — composition smoke tests.

  @Test
  fun railComposesForEveryRatio() {
    listOf(0f, -0.0f, 0.5f, 1f, Float.NaN).forEach { ratio ->
      runComposeUiTest {
        setKineticContent {
          KineticEvalRail(
            whiteRatio = ratio,
            displayValue = "+0.3",
            modifier = Modifier.height(200.dp),
          )
        }
        onNodeWithText("+0.3").assertIsDisplayed()
      }
    }
  }

  @Test
  fun railRendersNoTextWithoutDisplayValue() = runComposeUiTest {
    setKineticContent {
      KineticEvalRail(whiteRatio = 0.5f, displayValue = null, modifier = Modifier.height(200.dp))
    }
    onNodeWithText("+0.3").assertDoesNotExist()
  }

  @Test
  fun railComposesAtZeroHeight() = runComposeUiTest {
    setKineticContent {
      KineticEvalRail(whiteRatio = 0.5f, displayValue = "-M12", modifier = Modifier.height(0.dp))
    }
    onNodeWithText("-M12").assertExists()
  }

  @Test
  fun thinRailComposesAtFullWhite() = runComposeUiTest {
    setKineticContent {
      KineticEvalRail(
        whiteRatio = 1f,
        displayValue = "+9.9",
        modifier = Modifier.height(120.dp),
        thin = true,
      )
    }
    onNodeWithText("+9.9").assertExists()
  }

  private companion object {
    /** The rail's crisp marker is 2.dp thick; these tests work in raw pixels at density 1. */
    const val MARKER_THICKNESS = 2f
  }
}
