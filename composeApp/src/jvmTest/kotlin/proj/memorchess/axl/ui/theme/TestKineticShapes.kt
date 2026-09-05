package proj.memorchess.axl.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Density
import io.kotest.matchers.shouldBe
import kotlin.test.Test

/** Verifies each Material shape role in [kineticShapes] resolves to its intended corner radius. */
internal class TestKineticShapes {

  private val density = Density(density = 1f)
  private val size = Size(width = 100f, height = 100f)

  private fun cornerPx(shape: RoundedCornerShape): Float = shape.topStart.toPx(size, density)

  @Test
  fun pillChipBadgeRolesUseTheSmallerScale() {
    cornerPx(kineticShapes.extraSmall as RoundedCornerShape) shouldBe 12f
    cornerPx(kineticShapes.small as RoundedCornerShape) shouldBe 16f
  }

  @Test
  fun cardButtonSheetRolesUseTwentyDp() {
    cornerPx(kineticShapes.medium as RoundedCornerShape) shouldBe 20f
    cornerPx(kineticShapes.large as RoundedCornerShape) shouldBe 20f
    cornerPx(kineticShapes.extraLarge as RoundedCornerShape) shouldBe 20f
  }
}
