package proj.memorchess.axl.ui.components.board

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.time.Duration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import memorchess.composeapp.generated.resources.Res
import memorchess.composeapp.generated.resources.description_board_piece
import memorchess.composeapp.generated.resources.description_board_tile
import org.jetbrains.compose.resources.stringResource
import proj.memorchess.axl.core.config.CHESS_BOARD_COLOR_SETTING
import proj.memorchess.axl.core.config.MOVE_ANIMATION_DURATION_SETTING
import proj.memorchess.axl.core.engine.BoardLocation
import proj.memorchess.axl.core.engine.BoardUtils
import proj.memorchess.axl.core.engine.TileColor
import proj.memorchess.axl.ui.theme.KineticMotion

/**
 * Displays the chess board grid with interactive tiles and animated pieces.
 *
 * Renders the board tiles and pieces, handles tile selection, and animates piece movement. Uses
 * Compose for UI and coroutines for click handling and animation.
 *
 * @param state The board state containing tile, piece, and move information.
 * @param bestMoveArrow Arrow overlay data, or `null` to hide the arrow.
 * @param modifier Modifier for customizing the board layout.
 */
@Composable
fun BoardGrid(
  state: BoardGridState,
  bestMoveArrow: BestMoveArrowData? = null,
  modifier: Modifier = Modifier,
) {
  val scope = rememberCoroutineScope()
  val tilePositions = remember { mutableMapOf<BoardLocation, DpOffset>() }

  val animationDuration = MOVE_ANIMATION_DURATION_SETTING.getValue()
  BoxWithConstraints(modifier = modifier.aspectRatio(1f), contentAlignment = Alignment.Center) {
    // Derive every tile's offset from a single measurement. The board is uniform, so each tile is
    // maxWidth / 8; recording positions this way avoids the 64 per-tile BoxWithConstraints
    // (subcompositions) that used to make navigating to a board screen hitch. Offsets are a
    // coordinate space consumed only as deltas by AnimatedPiece, so they are independent of board
    // inversion.
    val tileSize = maxWidth / 8
    remember(tileSize) {
      for (row in 0..7) {
        for (col in 0..7) {
          tilePositions[BoardLocation(row, col)] = DpOffset(tileSize * col, -tileSize * row)
        }
      }
      tileSize
    }
    DrawTileGrid(state, scope)
    DrawPieceGrid(state, animationDuration, tilePositions)
    BestMoveArrow(bestMoveArrow, state.inverted, Modifier.fillMaxSize())
  }
}

/**
 * Composable function that draws the grid of tiles on the chessboard.
 *
 * The 64 cells exist only to carry the per-tile clickable semantics (click label and on-screen
 * position) that tests and accessibility rely on. Tile backgrounds and the selection highlight are
 * painted in a single [drawBehind] on the grid instead of per-cell composables, so selecting a tile
 * only invalidates the draw phase. Tile pixel positions for piece animation are derived once by
 * [BoardGrid] from a single measurement rather than per tile here.
 *
 * The click label template is resolved through [stringResource] once for the whole grid and
 * formatted per tile, because per-cell resource lookups dominated the board composition time.
 *
 * @param state The board state containing tile information and selection.
 * @param scope Coroutine scope for handling tile click events asynchronously.
 */
@Composable
private fun DrawTileGrid(state: BoardGridState, scope: CoroutineScope) {
  val colors = CHESS_BOARD_COLOR_SETTING.getValue()
  val borderWidth = with(LocalDensity.current) { 3.dp.toPx() }
  val tileDescriptionTemplate = stringResource(Res.string.description_board_tile)
  DrawGrid(
    modifier =
      Modifier.drawBehind {
        val tileSize = size.width / 8f
        val selected = state.selectedTile
        for (index in 0..63) {
          val location = state.getBoardLocationAt(index)
          val topLeft = Offset(index % 8 * tileSize, index / 8 * tileSize)
          drawRect(
            color =
              if (location.color == TileColor.BLACK) colors.darkSquareColor
              else colors.lightSquareColor,
            topLeft = topLeft,
            size = Size(tileSize, tileSize),
          )
          if (
            selected != null && selected.first == location.row && selected.second == location.col
          ) {
            // Inset by half the stroke so the border stays inside the tile, matching the previous
            // Modifier.border rendering.
            drawRect(
              color = colors.selectedBorderColor,
              topLeft = topLeft + Offset(borderWidth / 2f, borderWidth / 2f),
              size = Size(tileSize - borderWidth, tileSize - borderWidth),
              style = Stroke(borderWidth),
            )
          }
        }
      },
    boxModifier = {
      val location = state.getBoardLocationAt(it)
      val coords = Pair(location.row, location.col)
      val tileName = BoardUtils.tileName(location.row, location.col)
      val clickLabel = formatSingleArgTemplate(tileDescriptionTemplate, tileName)
      clickable(onClickLabel = clickLabel, onClick = { scope.launch { state.onTileClick(coords) } })
    },
  ) {}
}

/**
 * Composable function that draws the grid of chess pieces on the board.
 *
 * Renders each piece at its corresponding tile location. Handles piece animation when a move
 * occurs, and displays the piece at its destination after the animation completes.
 *
 * The piece content description template is resolved through [stringResource] once for the whole
 * grid and formatted per piece, because per-cell resource lookups dominated the board composition
 * time.
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
  val pieceDescriptionTemplate = stringResource(Res.string.description_board_piece)
  DrawGrid({ this }) {
    val location = state.getBoardLocationAt(it)
    Box(modifier = Modifier.aspectRatio(1f).weight(1f)) {
      val piece = state.tileToPiece[location]
      if (
        piece != null &&
          (animationDuration == Duration.ZERO ||
            (!state.piecesToMove.contains(location) &&
              !state.piecesToMove.values.contains(location)))
      ) {
        val tileName = BoardUtils.tileName(location.row, location.col)
        Piece(
          piece,
          Modifier.fillMaxSize().testTag("Piece $piece at $tileName"),
          contentDescription = formatSingleArgTemplate(pieceDescriptionTemplate, piece.toString()),
        )
      } else if (animationDuration != Duration.ZERO && state.piecesToMove.contains(location)) {
        AnimatedPiece(state, location, tilePositions, animationDuration, pieceDescriptionTemplate)
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
 * @param modifier Modifier applied to the grid as a whole, e.g. to paint all tile backgrounds.
 * @param boxContent Content composable for each grid tile, typically used to render tile or piece.
 */
@Composable
private fun DrawGrid(
  boxModifier: @Composable Modifier.(Int) -> Modifier,
  modifier: Modifier = Modifier,
  boxContent: @Composable (RowScope.(Int) -> Unit),
) {
  Column(modifier = modifier) {
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
 * @param location The board location of the piece to animate.
 * @param tilePositions Map of board locations to their pixel offsets on the board.
 * @param animationDuration Duration of the slide, sourced once from the caller.
 * @param pieceDescriptionTemplate Content description template, resolved once by the caller.
 */
@Composable
private fun AnimatedPiece(
  state: BoardGridState,
  location: BoardLocation,
  tilePositions: Map<BoardLocation, DpOffset>,
  animationDuration: Duration,
  pieceDescriptionTemplate: String,
) {
  val destinationLocation = state.piecesToMove[location]
  val startPos = tilePositions[location]
  checkNotNull(startPos) { "Tile at $location not positioned yet" }
  val endPos = tilePositions[destinationLocation]
  checkNotNull(endPos) { "Tile at $destinationLocation not positioned yet" }
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
          easing = KineticMotion.pieceGlide,
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
          easing = KineticMotion.pieceGlide,
        ),
    )
  val pieceToMove = state.tileToPiece[destinationLocation]
  checkNotNull(pieceToMove) { "No piece at $destinationLocation" }
  val offsetMultiplier = if (state.inverted) -1 else 1
  // Read the animated offsets inside the placement lambda so the slide runs in the layout phase
  // (no per-frame recomposition of the piece).
  Piece(
    pieceToMove,
    Modifier.offset {
        IntOffset((x * offsetMultiplier).roundToPx(), (y * offsetMultiplier).roundToPx())
      }
      .fillMaxSize(),
    contentDescription = formatSingleArgTemplate(pieceDescriptionTemplate, pieceToMove.toString()),
  )
  LaunchedEffect(Unit) {
    moved = true // NOSONAR: Compose state triggers recomposition
    delay(animationDuration)
    state.piecesToMove.remove(location)
  }
}

/**
 * Formats a single-placeholder string resource template without going through the resource
 * formatter, so bulk callers pay the resource lookup only once for the template.
 *
 * @param template Raw resource string containing a `%1$s` placeholder.
 * @param argument Value substituted for the placeholder.
 */
private fun formatSingleArgTemplate(template: String, argument: String): String {
  return template.replace($$"%1$s", argument)
}
