package proj.memorchess.axl.ui.components.board

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import proj.memorchess.axl.core.config.CHESS_BOARD_COLOR_SETTING
import proj.memorchess.axl.core.engine.board.ITile
import proj.memorchess.axl.core.engine.board.ITile.TileColor

@Composable
fun Tile(tile: ITile, modifier: Modifier = Modifier) {
  val colors = CHESS_BOARD_COLOR_SETTING.getValue()
  Box(
    modifier =
      modifier
        .background(
          if (tile.getColor() == TileColor.BLACK) colors.darkSquareColor
          else colors.lightSquareColor
        )
        .fillMaxSize()
  )
}
