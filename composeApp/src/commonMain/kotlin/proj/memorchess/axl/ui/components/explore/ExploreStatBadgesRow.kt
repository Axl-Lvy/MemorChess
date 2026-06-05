package proj.memorchess.axl.ui.components.explore

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import memorchess.composeapp.generated.resources.Res
import memorchess.composeapp.generated.resources.explore_stat_eval
import memorchess.composeapp.generated.resources.explore_stat_pos
import memorchess.composeapp.generated.resources.explore_stat_ret
import org.jetbrains.compose.resources.stringResource
import proj.memorchess.axl.ui.components.navigation.KineticTopBarPill
import proj.memorchess.axl.ui.theme.LocalKineticPalette

/**
 * Compact 3-pill stat row used on mobile above the moves trail. Reuses [KineticTopBarPill] for each
 * badge so the visual matches the top-bar meta strip.
 *
 * Values are placeholders for v1; wiring to real evaluation/position/retention data is left for a
 * follow-up wave.
 */
@Composable
fun ExploreStatBadgesRow(modifier: Modifier = Modifier) {
  val palette = LocalKineticPalette.current
  Row(
    modifier =
      modifier
        .fillMaxWidth()
        .height(44.dp)
        .background(palette.bg2)
        .border(width = 1.dp, color = palette.line),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.End,
  ) {
    KineticTopBarPill(
      label = stringResource(Res.string.explore_stat_eval),
      value = "+0.0",
      hot = false,
    )
    KineticTopBarPill(label = stringResource(Res.string.explore_stat_pos), value = "—", hot = false)
    KineticTopBarPill(label = stringResource(Res.string.explore_stat_ret), value = "—", hot = false)
  }
}
