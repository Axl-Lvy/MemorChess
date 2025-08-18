package proj.memorchess.axl.ui.components.board

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import kotlin.time.Duration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import memorchess.composeapp.generated.resources.Res
import memorchess.composeapp.generated.resources.description_board_tile
import org.jetbrains.compose.resources.stringResource
import proj.memorchess.axl.core.config.CHESS_BOARD_COLOR_SETTING
import proj.memorchess.axl.core.config.MOVE_ANIMATION_DURATION_SETTING
import proj.memorchess.axl.core.engine.board.BoardLocation
import proj.memorchess.axl.core.engine.board.ITile

/**
 * Displays the chess board grid with interactive tiles and animated pieces.
 *
 * Renders the board tiles and pieces, handles tile selection, and animates piece movement. Uses
 * Compose for UI and coroutines for click handling and animation.
 *
 * @param state The board state containing tile, piece, and move information.
 * @param modifier Modifier for customizing the board layout.
 */
@Composable
fun BoardGrid(state: BoardGridState, modifier: Modifier = Modifier) {
  val scope = rememberCoroutineScope()
  val tilePositions = remember { mutableMapOf<BoardLocation, DpOffset>() }

  val animationDuration = MOVE_ANIMATION_DURATION_SETTING.getValue()
  Box(modifier = modifier.aspectRatio(1f), contentAlignment = Alignment.Center) {
    DrawTileGrid(state, scope, tilePositions)
    DrawPieceGrid(state, animationDuration, tilePositions)
  }
}

/**
 * Composable function that draws the grid of tiles on the chessboard.
 *
 * Iterates over all board locations, rendering each tile and handling click events. Highlights the
 * selected tile and updates tile positions for piece animation.
 *
 * @param state The board state containing tile information and selection.
 * @param scope Coroutine scope for handling tile click events asynchronously.
 * @param tilePositions (out) Mutable map to store the positions of each tile for animation
 *   purposes.
 */
@Composable
private fun DrawTileGrid(
  state: BoardGridState,
  scope: CoroutineScope,
  tilePositions: MutableMap<BoardLocation, DpOffset>,
) {
  var tileWidth = remember<Dp?> { null }
  var tileHeight = remember<Dp?> { null }
  DrawGrid(
    boxModifier = {
      val tile = state.getTileAt(it)
      val coords = tile.getCoords()
      val isSelected = state.selectedTile == coords
      val clickLabel = stringResource(Res.string.description_board_tile, tile.getName())
      clickable(onClickLabel = clickLabel, onClick = { scope.launch { state.onTileClick(coords) } })
        .then(
          if (isSelected)
            Modifier.border(3.dp, CHESS_BOARD_COLOR_SETTING.getValue().selectedBorderColor)
          else Modifier
        )
    }
  ) {
    val tile = state.getTileAt(it)
    BoxWithConstraints {
      if (tileWidth == null || tileHeight == null) {
        tileWidth = maxWidth
        tileHeight = maxHeight
      }
      tilePositions[tile.boardLocation] =
        DpOffset(tileWidth * tile.getCoords().second, -tileHeight * tile.getCoords().first)
    }
    Tile(tile)
  }
}

/**
 * Composable function that draws the grid of chess pieces on the board.
 *
 * Renders each piece at its corresponding tile location. Handles piece animation when a move
 * occurs, and displays the piece at its destination after the animation completes.
 *
 * @param state The board state containing piece and move information.
 * @param animationDuration Duration for animating piece movement between tiles.
 * @param tilePositions Map of board locations to their pixel positions for animation.
 */
@Composable
private fun DrawPieceGrid(
  state: BoardGridState,
  animationDuration: Duration,
  tilePositions: Map<BoardLocation, DpOffset>,
) {
  DrawGrid({ this }) {
    val tile = state.getTileAt(it)
    Box(modifier = Modifier.aspectRatio(1f).weight(1f)) {
      val piece = state.tileToPiece[tile.boardLocation]
      if (
        piece != null &&
          (animationDuration == Duration.ZERO ||
            (!state.piecesToMove.contains(tile.boardLocation) &&
              !state.piecesToMove.values.contains(tile.boardLocation)))
      ) {
        Piece(piece, Modifier.fillMaxSize().testTag("Piece $piece at ${tile.getName()}"))
      } else if (
        animationDuration != Duration.ZERO && state.piecesToMove.contains(tile.boardLocation)
      ) {
        AnimatedPiece(state, tile, tilePositions)
      }
    }
  }
}

/**
 * Composable for drawing an 8x8 grid structure for the chess board.
 *
 * Iterates over rows and columns, applying the provided boxModifier and boxContent for each tile.
 *
 * @param boxModifier Modifier applied to each grid tile, can be used for click handling or styling.
 * @param boxContent Content composable for each grid tile, typically used to render tile or piece.
 */
@Composable
private fun DrawGrid(
  boxModifier: @Composable Modifier.(Int) -> Modifier,
  boxContent: @Composable (RowScope.(Int) -> Unit),
) {
  Column {
    for (rowIndex in 0..7) {
      Row(modifier = Modifier.fillMaxSize().weight(1f)) {
        for (colIndex in 0..7) {
          val index = rowIndex * 8 + colIndex
          Box(modifier = Modifier.aspectRatio(1f).weight(1f).boxModifier(index)) {
            this@Row.boxContent(index)
          }
        }
      }
    }
  }
}

/**
 * Composable for animating a chess piece movement on the board grid.
 *
 * Animates the piece from its current position to the destination tile using an offset animation.
 * Removes the piece from the move list after the animation completes.
 *
 * @param state The current board grid state containing piece and move information.
 * @param tile The tile representing the piece to animate.
 * @param tilePositions Map of grid items to their pixel offsets on the board.
 */
@Composable
private fun AnimatedPiece(
  state: BoardGridState,
  tile: ITile,
  tilePositions: Map<BoardLocation, DpOffset>,
) {
  val animationDuration = remember { MOVE_ANIMATION_DURATION_SETTING.getValue() }
  val destinationGridItem = state.piecesToMove[tile.boardLocation]
  val startPos = tilePositions[tile.boardLocation]
  checkNotNull(startPos) { "Tile at ${tile.boardLocation} not positioned yet" }
  val endPos = tilePositions[destinationGridItem]
  checkNotNull(endPos) { "Tile at $destinationGridItem not positioned yet" }
  val targetOffset = endPos - startPos
  var moved by remember { mutableStateOf(false) }
  val x by
    animateDpAsState(
      targetValue =
        if (moved) {
          targetOffset.x
        } else {
          0.dp
        },
      label = "x offset",
      animationSpec =
        tween(
          durationMillis = animationDuration.inWholeMilliseconds.toInt(),
          delayMillis = 0,
          easing = LinearEasing,
        ),
    )
  val y by
    animateDpAsState(
      targetValue =
        if (moved) {
          targetOffset.y
        } else {
          0.dp
        },
      label = "y offset",
      animationSpec =
        tween(
          durationMillis = animationDuration.inWholeMilliseconds.toInt(),
          delayMillis = 0,
          easing = LinearEasing,
        ),
    )
  val pieceToMove = state.tileToPiece[destinationGridItem]
  checkNotNull(pieceToMove) { "No piece at $destinationGridItem" }
  val offsetMultiplier = if (state.inverted) -1 else 1
  Piece(
    pieceToMove,
    Modifier.offset(x * offsetMultiplier, y * offsetMultiplier).fillMaxSize(),
  )
  LaunchedEffect(Unit) {
    moved = true
    delay(animationDuration)
    state.piecesToMove.remove(tile.boardLocation)
  }
}
