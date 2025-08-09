package proj.memorchess.axl.preview.layouts

import androidx.compose.runtime.Composable
import de.drick.compose.hotpreview.HotPreview
import proj.memorchess.axl.ui.layout.explore.LandscapeExploreLayout

@HotPreview(widthDp = 5500, heightDp = 1100, density = 2.625f)
@Composable
private fun LandscapeExploreLayoutPreview() {
  LandscapeExploreLayout(content = previewExploreLayoutContent)
}
