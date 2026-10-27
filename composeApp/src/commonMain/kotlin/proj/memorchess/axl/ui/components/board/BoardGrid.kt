package proj.memorchess.axl.ui.components.board

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
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
import proj.memorchess.axl.ui.theme.ChessBoardColorScheme
import proj.memorchess.axl.ui.theme.KineticMotion
import proj.memorchess.axl.ui.theme.KineticPalette
import proj.memorchess.axl.ui.theme.LocalKineticPalette

/** Distance the "+1" floater rises before fading out. */
private val PLUS_ONE_FLOAT_DISTANCE = 24.dp

/** Fraction of the tile size the correct-answer ring expands to, past the tile's own edge. */
private const val RING_MAX_RADIUS_FRACTION = 0.75f

/** Scale a correctly played square pops to. */
private const val SQUARE_POP_SCALE = 1.14f

/** Displays the interactive chess board with animated pieces and training feedback. */
@Composable
fun BoardGrid(
  state: BoardGridState,
  bestMoveArrow: BestMoveArrowData? = null,
  feedback: BoardTrainingFeedback = BoardTrainingFeedback(),
  modifier: Modifier = Modifier,
) {
  val scope = rememberCoroutineScope()
  val tilePositions = remember { mutableMapOf<BoardLocation, DpOffset>() }

  val animationDuration = MOVE_ANIMATION_DURATION_SETTING.getValue()

  // Hoisted here rather than into DrawTileGrid for z order: the floater must paint above every
  // piece and the arrow.
  val plusOneOffsetY = remember { Animatable(0f) }
  val plusOneAlpha = remember { Animatable(0f) }
  val plusOneFloatPx = with(LocalDensity.current) { PLUS_ONE_FLOAT_DISTANCE.toPx() }
  LaunchedEffect(feedback.attempt) {
    if (feedback.attempt <= 0) return@LaunchedEffect
    plusOneOffsetY.snapTo(0f)
    plusOneAlpha.snapTo(0f)
    if (
      feedback.isCorrect == true &&
        feedback.playedSquare != null &&
        KineticMotion.shouldAnimateBoardFeedback()
    ) {
      plusOneAlpha.snapTo(1f)
      launch {
        plusOneOffsetY.animateTo(-plusOneFloatPx, KineticMotion.Celebratory.correctAnswer())
      }
      plusOneAlpha.animateTo(0f, KineticMotion.Celebratory.correctAnswer())
    }
  }

  BoxWithConstraints(modifier = modifier.aspectRatio(1f), contentAlignment = Alignment.Center) {
    // One measurement instead of 64 per-tile subcompositions, which make board screens hitch.
    // AnimatedPiece only uses these offsets as deltas, so they ignore board inversion.
    val tileSize = maxWidth / 8
    remember(tileSize) {
      for (row in 0..7) {
        for (col in 0..7) {
          tilePositions[BoardLocation(row, col)] = DpOffset(tileSize * col, -tileSize * row)
        }
      }
      tileSize
    }
    DrawTileGrid(state, scope, feedback)
    DrawPieceGrid(state, animationDuration, tilePositions)
    BestMoveArrow(bestMoveArrow, state.inverted, Modifier.fillMaxSize())
    val playedSquare = feedback.playedSquare
    if (
      feedback.isCorrect == true &&
        playedSquare != null &&
        KineticMotion.shouldAnimateBoardFeedback()
    ) {
      PlusOneFloater(playedSquare, tileSize, state.inverted, plusOneOffsetY, plusOneAlpha)
    }
  }
}

/**
 * Draws the tiles and training feedback overlays. The 64 cells only carry click semantics; tiles
 * and overlays are painted in one [drawBehind], so selecting a tile only invalidates drawing.
 */
@Composable
private fun DrawTileGrid(
  state: BoardGridState,
  scope: CoroutineScope,
  feedback: BoardTrainingFeedback,
) {
  val colors = CHESS_BOARD_COLOR_SETTING.getValue()
  val palette = LocalKineticPalette.current
  val borderWidth = with(LocalDensity.current) { 3.dp.toPx() }
  // Resolved once for the grid: per-cell resource lookups dominated board composition time.
  val tileDescriptionTemplate = stringResource(Res.string.description_board_tile)

  // Driven by one LaunchedEffect(feedback.attempt); this composable stays mounted while the board
  // is shown, so the feedback fires exactly once per attempt.
  val squareScale = remember { Animatable(1f) }
  val wrongPulseAlpha = remember { Animatable(0f) }
  val ringRadiusFraction = remember { Animatable(0f) }
  val ringAlpha = remember { Animatable(0f) }
  val outlineAlpha = remember { Animatable(0f) } // Correct square's outline after a wrong answer.

  LaunchedEffect(feedback.attempt) {
    animateTileFeedback(
      feedback,
      squareScale,
      wrongPulseAlpha,
      ringRadiusFraction,
      ringAlpha,
      outlineAlpha,
    )
  }

  DrawGrid(
    modifier =
      Modifier.drawBehind {
        drawTilesAndOverlays(
          state,
          feedback,
          colors,
          palette,
          borderWidth,
          ringAlpha,
          ringRadiusFraction,
          outlineAlpha,
        )
      },
    boxModifier = {
      val location = state.getBoardLocationAt(it)
      val coords = Pair(location.row, location.col)
      val tileName = BoardUtils.tileName(location.row, location.col)
      val clickLabel = formatSingleArgTemplate(tileDescriptionTemplate, tileName)
      clickable(onClickLabel = clickLabel, onClick = { scope.launch { state.onTileClick(coords) } })
    },
  ) { index ->
    val location = state.getBoardLocationAt(index)
    val isCorrect = feedback.isCorrect
    if (location == feedback.playedSquare && isCorrect != null) {
      PlayedSquareOverlay(isCorrect, squareScale, wrongPulseAlpha, palette)
    }
  }
}

/**
 * Resets every animatable to rest, then plays [feedback]'s animation. Resetting first keeps a prior
 * attempt's stale value from turning `animateTo` into a no-op.
 */
private suspend fun CoroutineScope.animateTileFeedback(
  feedback: BoardTrainingFeedback,
  squareScale: Animatable<Float, AnimationVector1D>,
  wrongPulseAlpha: Animatable<Float, AnimationVector1D>,
  ringRadiusFraction: Animatable<Float, AnimationVector1D>,
  ringAlpha: Animatable<Float, AnimationVector1D>,
  outlineAlpha: Animatable<Float, AnimationVector1D>,
) {
  if (feedback.attempt <= 0) return
  squareScale.snapTo(1f)
  wrongPulseAlpha.snapTo(0f)
  ringRadiusFraction.snapTo(0f)
  ringAlpha.snapTo(0f)
  outlineAlpha.snapTo(0f)

  val animate = KineticMotion.shouldAnimateBoardFeedback()
  if (feedback.isCorrect == true && feedback.playedSquare != null) {
    if (animate) {
      launch {
        squareScale.animateTo(SQUARE_POP_SCALE, KineticMotion.Celebratory.correctAnswer())
        squareScale.animateTo(1f, KineticMotion.Celebratory.correctAnswer())
      }
      launch {
        ringAlpha.snapTo(1f)
        launch { ringRadiusFraction.animateTo(1f, KineticMotion.Celebratory.correctAnswer()) }
        ringAlpha.animateTo(0f, KineticMotion.Celebratory.correctAnswer())
      }
    }
  } else if (feedback.isCorrect == false && feedback.playedSquare != null) {
    if (animate) {
      wrongPulseAlpha.animateTo(1f, KineticMotion.Routine.wrongAnswer())
      wrongPulseAlpha.animateTo(0f, KineticMotion.Routine.wrongAnswer())
    }
    if (feedback.correctSquare != null) {
      if (animate) outlineAlpha.animateTo(1f, KineticMotion.Routine.wrongAnswer())
      else outlineAlpha.snapTo(1f)
    }
  }
}

/**
 * Paints every tile background, then every overlay in a second pass, so a later tile's background
 * never covers an overlay, like the ring, that spills past its own tile.
 */
private fun DrawScope.drawTilesAndOverlays(
  state: BoardGridState,
  feedback: BoardTrainingFeedback,
  colors: ChessBoardColorScheme,
  palette: KineticPalette,
  borderWidth: Float,
  ringAlpha: Animatable<Float, AnimationVector1D>,
  ringRadiusFraction: Animatable<Float, AnimationVector1D>,
  outlineAlpha: Animatable<Float, AnimationVector1D>,
) {
  val tileSize = size.width / 8f
  val selected = state.selectedTile

  for (index in 0..63) {
    val location = state.getBoardLocationAt(index)
    val topLeft = Offset(index % 8 * tileSize, index / 8 * tileSize)
    drawRect(
      color =
        if (location.color == TileColor.BLACK) colors.darkSquareColor else colors.lightSquareColor,
      topLeft = topLeft,
      size = Size(tileSize, tileSize),
    )
  }
  for (index in 0..63) {
    val location = state.getBoardLocationAt(index)
    val topLeft = Offset(index % 8 * tileSize, index / 8 * tileSize)
    val isSelected =
      selected != null && selected.first == location.row && selected.second == location.col
    // Before the selection border, which shares its inset, so a hinted tile still shows selection.
    if (location in feedback.hintSquares) {
      drawRect(
        color = palette.action,
        topLeft = topLeft + Offset(borderWidth / 2f, borderWidth / 2f),
        size = Size(tileSize - borderWidth, tileSize - borderWidth),
        style = Stroke(borderWidth),
      )
    }
    if (isSelected) {
      // Inset by half the stroke so the border stays inside the tile.
      drawRect(
        color = colors.selectedBorderColor,
        topLeft = topLeft + Offset(borderWidth / 2f, borderWidth / 2f),
        size = Size(tileSize - borderWidth, tileSize - borderWidth),
        style = Stroke(borderWidth),
      )
    }
    if (location == feedback.playedSquare && ringAlpha.value > 0f) {
      drawCircle(
        color = palette.progress,
        radius = tileSize * RING_MAX_RADIUS_FRACTION * ringRadiusFraction.value,
        center = topLeft + Offset(tileSize / 2f, tileSize / 2f),
        alpha = ringAlpha.value,
        style = Stroke(width = borderWidth),
      )
    }
    if (location == feedback.correctSquare && outlineAlpha.value > 0f) {
      drawRect(
        color = palette.progress,
        topLeft = topLeft + Offset(borderWidth / 2f, borderWidth / 2f),
        size = Size(tileSize - borderWidth, tileSize - borderWidth),
        style = Stroke(borderWidth),
        alpha = outlineAlpha.value,
      )
    }
  }
}

/**
 * Played-square feedback overlay: a scale pop with a soft flash on a correct answer, a pink pulse
 * flash on a wrong one.
 */
@Composable
private fun PlayedSquareOverlay(
  isCorrect: Boolean,
  squareScale: Animatable<Float, AnimationVector1D>,
  wrongPulseAlpha: Animatable<Float, AnimationVector1D>,
  palette: KineticPalette,
) {
  if (isCorrect) {
    Box(
      Modifier.fillMaxSize()
        .graphicsLayer {
          scaleX = squareScale.value
          scaleY = squareScale.value
        }
        .drawBehind {
          val ramp = ((squareScale.value - 1f) / (SQUARE_POP_SCALE - 1f)).coerceIn(0f, 1f)
          drawRect(palette.progressSoft, alpha = ramp)
        }
    )
  } else {
    Box(
      Modifier.fillMaxSize().drawBehind {
        if (wrongPulseAlpha.value > 0f) {
          drawRect(color = palette.destructive, alpha = wrongPulseAlpha.value)
        }
      }
    )
  }
}

/** Draws the pieces, animating the ones currently moving. */
@Composable
private fun DrawPieceGrid(
  state: BoardGridState,
  animationDuration: Duration,
  tilePositions: Map<BoardLocation, DpOffset>,
) {
  // Resolved once for the grid: per-cell resource lookups dominated board composition time.
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

/** Lays out an 8x8 grid, applying [boxModifier] and [boxContent] to each tile index. */
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

/** Slides the piece at [location] to its destination, then removes it from the pending moves. */
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
  // Read inside the placement lambda so the slide runs in layout, without per-frame recomposition.
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

/** Substitutes [argument] for the `%1$s` placeholder of [template], bypassing the formatter. */
private fun formatSingleArgTemplate(template: String, argument: String): String {
  return template.replace($$"%1$s", argument)
}
