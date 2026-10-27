package proj.memorchess.axl.ui.components.board

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import memorchess.composeapp.generated.resources.Res
import memorchess.composeapp.generated.resources.training_plus_one
import org.jetbrains.compose.resources.stringResource
import proj.memorchess.axl.core.engine.BoardLocation
import proj.memorchess.axl.ui.theme.LocalKineticPalette
import proj.memorchess.axl.ui.theme.LocalKineticTypography

/**
 * Floating "+1" that rises above [square] on a correct answer. Composed only while shown, never as
 * a zero-alpha node, so its presence in the tree is meaningful.
 */
@Composable
internal fun PlusOneFloater(
  square: BoardLocation,
  tileSize: Dp,
  inverted: Boolean,
  offsetY: Animatable<Float, AnimationVector1D>,
  alpha: Animatable<Float, AnimationVector1D>,
) {
  val palette = LocalKineticPalette.current
  val typography = LocalKineticTypography.current
  val baseX = if (inverted) tileSize * (7 - square.col) else tileSize * square.col
  val baseY = if (inverted) tileSize * square.row else tileSize * (7 - square.row)
  // Without the fillMaxSize Box, the parent centers the tile box before the offset applies, so the
  // floater would anchor to the center instead of top-start like the other board overlays.
  Box(Modifier.fillMaxSize()) {
    Box(Modifier.offset(x = baseX, y = baseY).size(tileSize), contentAlignment = Alignment.Center) {
      Text(
        text = stringResource(Res.string.training_plus_one),
        modifier =
          Modifier.testTag("training-plus-one")
            .offset { IntOffset(0, offsetY.value.roundToInt()) }
            .graphicsLayer { this.alpha = alpha.value.coerceIn(0f, 1f) },
        style = typography.displaySm.copy(color = palette.progress),
      )
    }
  }
}
