package proj.memorchess.axl.ui.components.board

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import proj.memorchess.axl.core.config.CHESS_BOARD_COLOR_SETTING
import proj.memorchess.axl.core.engine.BoardLocation
import proj.memorchess.axl.core.engine.PieceKind
import proj.memorchess.axl.core.engine.evaluation.BestMove

private const val SHAFT_WIDTH_FACTOR = 0.12f
private const val ARROWHEAD_WIDTH_FACTOR = 0.3f
private const val ARROWHEAD_LENGTH_FACTOR = 0.3f
private const val CASTLING_COL_DELTA = 2
private const val KINGSIDE_ROOK_COL = 7
private const val KINGSIDE_ROOK_DEST_COL = 5
private const val QUEENSIDE_ROOK_COL = 0
private const val QUEENSIDE_ROOK_DEST_COL = 3

/**
 * All information needed to draw a best-move arrow on the board.
 *
 * @property move The engine's recommended move.
 * @property pieceKind The kind of piece being moved, used to detect knights and castling.
 */
data class BestMoveArrowData(val move: BestMove, val pieceKind: PieceKind?)

/**
 * Draws an arrow overlay on the board indicating the engine's best move.
 *
 * Renders a straight arrow for most moves, an L-shaped arrow for knight moves, and a double arrow
 * (king + rook) for castling.
 *
 * @param data The arrow data, or `null` to draw nothing.
 * @param inverted Whether the board is flipped (Black's perspective).
 * @param modifier Modifier for the canvas.
 */
@Composable
fun BestMoveArrow(data: BestMoveArrowData?, inverted: Boolean, modifier: Modifier = Modifier) {
  if (data == null) return
  val arrowColor = CHESS_BOARD_COLOR_SETTING.getValue().arrowColor
  Canvas(modifier = modifier) {
    val tileSize = size.width / 8f
    val from = toPixel(data.move.from, inverted, tileSize)
    val to = toPixel(data.move.to, inverted, tileSize)

    val isCastling =
      data.pieceKind == PieceKind.KING &&
        abs(data.move.from.col - data.move.to.col) == CASTLING_COL_DELTA
    when {
      isCastling -> drawCastlingArrows(data.move, inverted, tileSize, arrowColor)
      data.pieceKind == PieceKind.KNIGHT ->
        drawKnightArrow(from, to, data.move, inverted, tileSize, arrowColor)
      else -> drawStraightArrow(from, to, tileSize, arrowColor)
    }
  }
}

/** Converts a [BoardLocation] to a pixel-center [Offset] inside the canvas. */
private fun toPixel(location: BoardLocation, inverted: Boolean, tileSize: Float): Offset {
  val x = if (inverted) (7 - location.col + 0.5f) * tileSize else (location.col + 0.5f) * tileSize
  val y = if (inverted) (location.row + 0.5f) * tileSize else (7 - location.row + 0.5f) * tileSize
  return Offset(x, y)
}

/** Draws a straight arrow with a triangular arrowhead. */
private fun DrawScope.drawStraightArrow(from: Offset, to: Offset, tileSize: Float, color: Color) {
  val angle = atan2(to.y - from.y, to.x - from.x)
  val headLength = tileSize * ARROWHEAD_LENGTH_FACTOR
  val shaftEnd = Offset(to.x - cos(angle) * headLength, to.y - sin(angle) * headLength)

  drawLine(
    color = color,
    start = from,
    end = shaftEnd,
    strokeWidth = tileSize * SHAFT_WIDTH_FACTOR,
    cap = StrokeCap.Round,
  )
  drawArrowhead(to, angle, tileSize, color)
}

/** Draws an L-shaped arrow for knight moves: two perpendicular segments. */
private fun DrawScope.drawKnightArrow(
  from: Offset,
  to: Offset,
  bestMove: BestMove,
  inverted: Boolean,
  tileSize: Float,
  color: Color,
) {
  val rowDelta = abs(bestMove.to.row - bestMove.from.row)
  val colDelta = abs(bestMove.to.col - bestMove.from.col)

  // The corner of the L is along the longer axis from the source, then turns to the shorter axis.
  val corner =
    if (rowDelta > colDelta) {
      // Move 2 rows first, then 1 col
      BoardLocation(bestMove.to.row, bestMove.from.col)
    } else {
      // Move 2 cols first, then 1 row
      BoardLocation(bestMove.from.row, bestMove.to.col)
    }
  val cornerPixel = toPixel(corner, inverted, tileSize)
  val strokeWidth = tileSize * SHAFT_WIDTH_FACTOR

  // First leg
  drawLine(
    color = color,
    start = from,
    end = cornerPixel,
    strokeWidth = strokeWidth,
    cap = StrokeCap.Round,
  )

  // Second leg (shortened by arrowhead length)
  val angle = atan2(to.y - cornerPixel.y, to.x - cornerPixel.x)
  val headLength = tileSize * ARROWHEAD_LENGTH_FACTOR
  val shaftEnd = Offset(to.x - cos(angle) * headLength, to.y - sin(angle) * headLength)

  drawLine(
    color = color,
    start = cornerPixel,
    end = shaftEnd,
    strokeWidth = strokeWidth,
    cap = StrokeCap.Round,
  )
  drawArrowhead(to, angle, tileSize, color)
}

/** Draws two straight arrows for castling: one for the king, one for the rook. */
private fun DrawScope.drawCastlingArrows(
  bestMove: BestMove,
  inverted: Boolean,
  tileSize: Float,
  color: Color,
) {
  // King arrow
  val kingFrom = toPixel(bestMove.from, inverted, tileSize)
  val kingTo = toPixel(bestMove.to, inverted, tileSize)
  drawStraightArrow(kingFrom, kingTo, tileSize, color)

  // Rook arrow
  val isKingside = bestMove.to.col > bestMove.from.col
  val row = bestMove.from.row
  val rookFrom =
    if (isKingside) BoardLocation(row, KINGSIDE_ROOK_COL)
    else BoardLocation(row, QUEENSIDE_ROOK_COL)
  val rookTo =
    if (isKingside) BoardLocation(row, KINGSIDE_ROOK_DEST_COL)
    else BoardLocation(row, QUEENSIDE_ROOK_DEST_COL)
  drawStraightArrow(
    toPixel(rookFrom, inverted, tileSize),
    toPixel(rookTo, inverted, tileSize),
    tileSize,
    color,
  )
}

/** Draws a filled triangular arrowhead pointing in [angle] direction at [tip]. */
private fun DrawScope.drawArrowhead(tip: Offset, angle: Float, tileSize: Float, color: Color) {
  val headWidth = tileSize * ARROWHEAD_WIDTH_FACTOR / 2f
  val headLength = tileSize * ARROWHEAD_LENGTH_FACTOR
  val path =
    Path().apply {
      moveTo(tip.x, tip.y)
      lineTo(
        tip.x - cos(angle) * headLength - sin(angle) * headWidth,
        tip.y - sin(angle) * headLength + cos(angle) * headWidth,
      )
      lineTo(
        tip.x - cos(angle) * headLength + sin(angle) * headWidth,
        tip.y - sin(angle) * headLength - cos(angle) * headWidth,
      )
      close()
    }
  drawPath(path, color)
}
