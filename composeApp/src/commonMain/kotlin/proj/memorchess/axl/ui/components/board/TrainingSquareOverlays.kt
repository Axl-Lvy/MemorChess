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
 * Floating "+1" that rises above [square] on a correct answer, absolutely placed against
 * [BoardGrid]'s own [tileSize], the same row/col-to-pixel convention [BestMoveArrow] uses.
 *
 * Only composed while [BoardGrid] decides a correct move was just played. It is never a permanently
 * present, zero-alpha node, so its existence in the tree is itself meaningful.
 *
 * @param square The board square the floater rises above.
 * @param tileSize The board's per-tile size, used to convert [square] to a pixel offset.
 * @param inverted Whether the board is flipped (Black's perspective).
 * @param offsetY Vertical rise, read inside a layout-phase [Modifier.offset] lambda.
 * @param alpha Fade, read inside a draw-phase [Modifier.graphicsLayer] lambda.
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
  // The outer fillMaxSize Box is load-bearing: without it, Modifier.offset's own size(tileSize)
  // box is what the parent BoxWithConstraints measures and centers before the offset applies, so
  // the floater would anchor to (parent center + baseX/baseY) instead of (top-start +
  // baseX/baseY), the convention every other board overlay (DrawTileGrid, DrawPieceGrid,
  // BestMoveArrow) uses.
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
