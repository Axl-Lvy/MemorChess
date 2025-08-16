package proj.memorchess.axl.ui.components.board

import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.collections.set
import kotlin.time.Duration
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
  val tilePositions = remember { mutableMapOf<BoardLocation, Offset>() }
  var tileH by remember { mutableStateOf<Dp?>(null) }
  var tileV by remember { mutableStateOf<Dp?>(null) }

  val animationDuration = MOVE_ANIMATION_DURATION_SETTING.getValue()
  Box(modifier = modifier.aspectRatio(1f), contentAlignment = Alignment.Center) {
    // Draw tile grid
    DrawGrid(
      boxModifier = {
        val tile = state.getTileAt(it)
        val coords = tile.getCoords()
        val isSelected = state.selectedTile == coords
        val clickLabel = stringResource(Res.string.description_board_tile, tile.getName())
        clickable(
            onClickLabel = clickLabel,
            onClick = { scope.launch { state.onTileClick(coords) } },
          )
          .then(
            if (isSelected)
              Modifier.border(3.dp, CHESS_BOARD_COLOR_SETTING.getValue().selectedBorderColor)
            else Modifier
          )
      }
    ) {
      val tile = state.getTileAt(it)
      BoxWithConstraints {
        if (tileH == null) {
          tileH = maxWidth
        }
        if (tileV == null) {
          tileV = maxHeight
        }
      }
      Tile(
        tile,
        modifier =
          Modifier.onGloballyPositioned { layoutCoordinates ->
            tilePositions[tile.boardLocation] = layoutCoordinates.positionInRoot()
          },
      )
    }

    // Draw piece grid
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
  tilePositions: MutableMap<BoardLocation, Offset>,
) {
  val animationDuration = remember { MOVE_ANIMATION_DURATION_SETTING.getValue() }
  val destinationGridItem = state.piecesToMove[tile.boardLocation]
  val startPos = tilePositions[tile.boardLocation]
  checkNotNull(startPos) { "Tile at ${tile.boardLocation} not positioned yet" }
  val endPos = tilePositions[destinationGridItem]
  checkNotNull(endPos) { "Tile at $destinationGridItem not positioned yet" }
  val targetOffset = endPos - startPos
  var moved by remember { mutableStateOf(false) }
  val offset by
    animateOffsetAsState(
      targetValue =
        if (moved) {
          targetOffset
        } else {
          Offset.Zero
        },
      label = "offset",
      animationSpec =
        tween(durationMillis = animationDuration.inWholeMilliseconds.toInt(), delayMillis = 0),
    )
  val pieceToMove = state.tileToPiece[destinationGridItem]
  checkNotNull(pieceToMove) { "No piece at $destinationGridItem" }
  Piece(pieceToMove, Modifier.fillMaxSize().offset(offset.x.dp, offset.y.dp))
  LaunchedEffect(Unit) {
    moved = true
    delay(animationDuration)
    state.piecesToMove.remove(tile.boardLocation)
  }
}
