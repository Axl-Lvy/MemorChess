package proj.memorchess.axl.preview.board

import androidx.compose.runtime.Composable
import de.drick.compose.hotpreview.HotPreview
import org.koin.compose.koinInject
import proj.memorchess.axl.core.graph.TreeStore
import proj.memorchess.axl.core.interactions.LinesExplorer
import proj.memorchess.axl.ui.components.board.Board

@HotPreview(density = 1.0f, widthDp = 4000, heightDp = 4000, captionBar = true)
@Composable
private fun BoardPreview(treeStore: TreeStore = koinInject()) {
  Board(inverted = false, interactionsManager = LinesExplorer(treeStore = treeStore))
}
