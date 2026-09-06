package proj.memorchess.axl.ui.components.explore

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
 * The row is a 12.dp card with a 1.5.dp stroke, clipped so the pills' full-height separator stripes
 * stay inside the rounded corners.
 *
 * Values are placeholders for v1; wiring to real evaluation/position/retention data is left for a
 * follow-up wave.
 *
 * @param modifier External modifier applied to the row.
 */
@Composable
fun ExploreStatBadgesRow(modifier: Modifier = Modifier) {
  val palette = LocalKineticPalette.current
  val rowShape = MaterialTheme.shapes.extraSmall
  Row(
    modifier =
      modifier
        .fillMaxWidth()
        .height(44.dp)
        .clip(rowShape)
        .background(palette.bg2, rowShape)
        .border(width = 1.5.dp, color = palette.line, shape = rowShape),
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
