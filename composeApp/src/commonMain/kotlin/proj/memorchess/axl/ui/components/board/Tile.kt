package proj.memorchess.axl.ui.components.board

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import proj.memorchess.axl.core.config.CHESS_BOARD_COLOR_SETTING
import proj.memorchess.axl.core.engine.BoardLocation
import proj.memorchess.axl.core.engine.TileColor

@Composable
fun Tile(location: BoardLocation, modifier: Modifier = Modifier) {
  val colors = CHESS_BOARD_COLOR_SETTING.getValue()
  Box(
    modifier =
      modifier
        .background(
          if (location.color == TileColor.BLACK) colors.darkSquareColor else colors.lightSquareColor
        )
        .fillMaxSize()
  )
}
