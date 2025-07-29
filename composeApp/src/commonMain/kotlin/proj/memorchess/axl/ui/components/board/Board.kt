package proj.memorchess.axl.ui.components.board

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import de.drick.compose.hotpreview.HotPreview
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import memorchess.composeapp.generated.resources.Res
import memorchess.composeapp.generated.resources.description_board_tile
import org.jetbrains.compose.resources.stringResource
import proj.memorchess.axl.core.engine.board.ITile
import proj.memorchess.axl.core.engine.pieces.Piece
import proj.memorchess.axl.core.engine.pieces.vectors.Bishop
import proj.memorchess.axl.core.engine.pieces.vectors.Knight
import proj.memorchess.axl.core.engine.pieces.vectors.Queen
import proj.memorchess.axl.core.engine.pieces.vectors.Rook
import proj.memorchess.axl.core.interactions.InteractionsManager
import proj.memorchess.axl.core.interactions.LinesExplorer
import proj.memorchess.axl.core.util.Reloader
import proj.memorchess.axl.ui.util.BasicReloader

@Composable
fun Board(
  inverted: Boolean = false,
  interactionsManager: InteractionsManager,
  reloader: Reloader,
  modifier: Modifier = Modifier,
) {
  val drawableBoard =
    remember(interactionsManager.game.position, inverted, reloader.getKey()) {
      DrawableBoard(inverted, interactionsManager, reloader)
    }
  // Ensure the board area is square and overlays are relative to it
  Box(modifier = modifier.aspectRatio(1f), contentAlignment = Alignment.Center) {
    drawableBoard.DrawBoard(Modifier.fillMaxSize())
    if (interactionsManager.needPromotion.value) {
      // Center the PromotionSelector relative to the board
      Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        drawableBoard.PromotionSelector()
      }
    }
  }
}

private class DrawableBoard(
  private val inverted: Boolean,
  private val interactionsManager: InteractionsManager,
  private val reloader: Reloader,
) {
  private val board = interactionsManager.game.position.board
  private val tileToPiece =
    mutableStateMapOf<ITile, Piece?>().apply {
      board.getTilesIterator().forEach { put(it, it.getSafePiece()) }
    }

  private val scope = CoroutineScope(Dispatchers.Default)

  @Composable
  fun DrawBoard(modifier: Modifier) {
    LazyVerticalGrid(columns = GridCells.Fixed(8), modifier = modifier) {
      items(64) { index ->
        val coords = squareIndexToBoardTile(index)
        val tile = board.getTile(coords)
        run {
          Box(
            modifier =
              Modifier.clickable(
                  onClick = {
                    scope.launch {
                      interactionsManager.clickOnTile(coords, reloader)
                      for (boardTile in board.getTilesIterator()) {
                        if (tileToPiece[boardTile] != boardTile.getSafePiece()) {
                          tileToPiece[boardTile] = boardTile.getSafePiece()
                        }
                      }
                    }
                  },
                  onClickLabel = stringResource(Res.string.description_board_tile, tile.getName()),
                )
                .aspectRatio(1f)
          ) {
            Tile(tile)
            tileToPiece[tile]?.let { Piece(it, Modifier.fillMaxSize()) }
          }
        }
      }
    }
  }

  @Composable
  fun PromotionSelector() {
    val player = interactionsManager.game.position.playerTurn
    val possibilities = listOf<Piece>(Queen(player), Rook(player), Bishop(player), Knight(player))
    Row(
      modifier =
        Modifier.clip(RoundedCornerShape(24.dp))
          .background(Color.Black.copy(alpha = 0.7f))
          .padding(16.dp)
    ) {
      possibilities.forEach { piece ->
        Box(
          modifier =
            Modifier.size(56.dp)
              .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
              .background(Color.White.copy(alpha = 0.85f))
              .padding(8.dp)
              .clickable(
                onClick = {
                  scope.launch { interactionsManager.applyPromotion(piece.toString(), reloader) }
                },
                onClickLabel = "Promote to ${piece.toString().lowercase()}",
              )
        ) {
          Piece(piece, Modifier.fillMaxSize())
        }
        Spacer(modifier = Modifier.width(12.dp))
      }
    }
  }

  private fun squareIndexToBoardTile(index: Int): Pair<Int, Int> {
    return if (inverted) {
      Pair(index / 8, (63 - index) % 8)
    } else {
      Pair((63 - index) / 8, index % 8)
    }
  }
}

@HotPreview(density = 1.0f, widthDp = 4000, heightDp = 2000, captionBar = true)
@Composable
private fun BoardHotPreview() {
  Board(inverted = false, interactionsManager = LinesExplorer(), reloader = BasicReloader())
}
