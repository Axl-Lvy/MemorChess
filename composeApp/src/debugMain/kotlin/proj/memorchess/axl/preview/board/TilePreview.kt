package proj.memorchess.axl.preview.board

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import de.drick.compose.hotpreview.HotPreview
import de.drick.compose.hotpreview.HotPreviewParameter
import de.drick.compose.hotpreview.HotPreviewParameterProvider
import proj.memorchess.axl.core.config.CHESS_BOARD_COLOR_SETTING
import proj.memorchess.axl.core.engine.board.Tile
import proj.memorchess.axl.ui.components.board.Tile
import proj.memorchess.axl.ui.theme.ChessBoardColorScheme

@HotPreview(density = 1.0f, widthDp = 200, heightDp = 100, captionBar = false)
@Composable
private fun TilePreview(
  @HotPreviewParameter(TilePreviewProvider::class) params: ChessBoardColorScheme
) {
  CHESS_BOARD_COLOR_SETTING.setValue(params)
  Column(modifier = Modifier.fillMaxSize()) {
    Text(params.displayName, modifier = Modifier.weight(1f))
    Row(modifier = Modifier.weight(5f)) {
      Box(modifier = Modifier.weight(1f)) { Tile(Tile(0, 0)) }
      Box(modifier = Modifier.weight(1f)) { Tile(Tile(0, 1)) }
    }
  }
}

class TilePreviewProvider : HotPreviewParameterProvider<ChessBoardColorScheme> {
  override val values: Sequence<ChessBoardColorScheme>
    get() {
      return ChessBoardColorScheme.entries.asSequence()
    }
}
