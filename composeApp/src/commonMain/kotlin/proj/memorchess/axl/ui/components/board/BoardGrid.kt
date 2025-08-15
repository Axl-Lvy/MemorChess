package proj.memorchess.axl.ui.components.board

import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.diamondedge.logging.logging
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import memorchess.composeapp.generated.resources.Res
import memorchess.composeapp.generated.resources.description_board_tile
import org.jetbrains.compose.resources.stringResource
import proj.memorchess.axl.core.config.CHESS_BOARD_COLOR_SETTING
import proj.memorchess.axl.core.config.MOVE_ANIMATION_DURATION_SETTING
import proj.memorchess.axl.core.engine.board.GridItem
import proj.memorchess.axl.core.engine.board.ITile

@Composable
fun BoardGrid(state: BoardGridState, modifier: Modifier = Modifier) {
  val scope = rememberCoroutineScope()
  val tilePositions = remember { mutableMapOf<GridItem, Offset>() }
  var tileH by remember { mutableStateOf<Dp?>(null) }
  var tileV by remember { mutableStateOf<Dp?>(null) }

  Box(modifier = modifier.aspectRatio(1f), contentAlignment = Alignment.Center) {
    Column {
      for (rowIndex in 0..7) {
        Row(modifier = Modifier.fillMaxSize().weight(1f)) {
          for (colIndex in 0..7) {
            val index = rowIndex * 8 + colIndex
            val tile = state.getTileAt(index)
            val coords = tile.getCoords()
            val clickLabel = stringResource(Res.string.description_board_tile, tile.getName())
            val isSelected = state.selectedTile == coords

            Box(
              modifier =
                Modifier.clickable(
                    onClickLabel = clickLabel,
                    onClick = { scope.launch { state.onTileClick(coords) } },
                  )
                  .aspectRatio(1f)
                  .weight(1f)
                  .then(
                    if (isSelected)
                      Modifier.border(
                        3.dp,
                        CHESS_BOARD_COLOR_SETTING.getValue().selectedBorderColor,
                      )
                    else Modifier
                  )
            ) {
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
                    tilePositions[tile.gridItem] = layoutCoordinates.positionInRoot()
                  },
              )
            }
          }
        }
      }
    }
    Column {
      for (rowIndex in 0..7) {
        Row(modifier = Modifier.fillMaxSize().weight(1f)) {
          for (colIndex in 0..7) {
            val index = rowIndex * 8 + colIndex
            val tile = state.getTileAt(index)
            Box(modifier = Modifier.aspectRatio(1f).weight(1f)) {
              val piece = state.tileToPiece[tile.gridItem]
              if (
                piece != null &&
                  !state.piecesToMove.contains(tile.gridItem) &&
                  !state.piecesToMove.values.contains(tile.gridItem)
              ) {
                Piece(piece, Modifier.fillMaxSize())
              } else if (state.piecesToMove.contains(tile.gridItem)) {
                AnimatedPiece(state, tile, tilePositions)
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun AnimatedPiece(
  state: BoardGridState,
  tile: ITile,
  tilePositions: MutableMap<GridItem, Offset>,
) {
  val animationDuration = remember { MOVE_ANIMATION_DURATION_SETTING.getValue() }
  val destinationGridItem = state.piecesToMove[tile.gridItem]
  val startPos = tilePositions[tile.gridItem]
  checkNotNull(startPos) { "Tile at ${tile.gridItem} not positioned yet" }
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
  Piece(pieceToMove, Modifier.fillMaxSize().offset(offset.x.dp, offset.y.dp).zIndex(2f))
  LaunchedEffect(Unit) {
    delay(10)
    moved = true
  }
  LaunchedEffect(Unit) {
    delay(animationDuration)
    state.piecesToMove.remove(tile.gridItem)
  }
}
