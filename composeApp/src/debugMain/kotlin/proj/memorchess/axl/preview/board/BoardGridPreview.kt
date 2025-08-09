package proj.memorchess.axl.preview.board

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import de.drick.compose.hotpreview.DisplayCutoutMode
import de.drick.compose.hotpreview.HotPreview
import de.drick.compose.hotpreview.HotPreviewParameter
import de.drick.compose.hotpreview.HotPreviewParameterProvider
import de.drick.compose.hotpreview.NavigationBarMode
import proj.memorchess.axl.core.config.CHESS_BOARD_COLOR_SETTING
import proj.memorchess.axl.core.interactions.LinesExplorer
import proj.memorchess.axl.ui.components.board.BoardGrid
import proj.memorchess.axl.ui.components.board.BoardGridState
import proj.memorchess.axl.ui.theme.ChessBoardColorScheme

@HotPreview(
  density = 2.625f,
  widthDp = 411,
  heightDp = 891,
  statusBar = true,
  navigationBar = NavigationBarMode.GestureBottom,
  displayCutout = DisplayCutoutMode.CameraTop,
)
@Composable
private fun BoardGridPreview(
  @HotPreviewParameter(BoardGridPreviewProvider::class) params: ChessBoardColorScheme
) {
  CHESS_BOARD_COLOR_SETTING.setValue(params)
  Column(modifier = Modifier.fillMaxSize()) {
    Text(params.displayName, modifier = Modifier.weight(1f))
    Box(modifier = Modifier.weight(8f)) {
      BoardGrid(BoardGridState(false, LinesExplorer()), Modifier.fillMaxSize())
    }
  }
}

class BoardGridPreviewProvider : HotPreviewParameterProvider<ChessBoardColorScheme> {
  override val values: Sequence<ChessBoardColorScheme>
    get() {
      return ChessBoardColorScheme.entries.asSequence()
    }
}
