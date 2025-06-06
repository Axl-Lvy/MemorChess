package proj.memorchess.axl.ui.components.board

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import proj.memorchess.axl.core.engine.board.ITile
import proj.memorchess.axl.core.engine.board.ITile.TileColor

@Composable
fun Tile(tile: ITile) {

  Box(
    modifier =
      Modifier.background(
          if (tile.getColor() == TileColor.BLACK) Color.hsl(41.351353f, 0.9736843f, 0.14901961f)
          else Color.hsl(37.500004f, 0.8767123f, 0.7137255f)
        )
        .fillMaxSize()
  )
}
