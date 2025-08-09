package proj.memorchess.axl.preview.layouts

import androidx.compose.runtime.Composable
import de.drick.compose.hotpreview.HotPreview
import proj.memorchess.axl.ui.layout.explore.PortraitExploreLayout

@HotPreview(widthDp = 411, heightDp = 891, density = 2.625f)
@Composable
private fun PortraitExploreLayoutPreview() {
  PortraitExploreLayout(content = previewExploreLayoutContent)
}
