package proj.memorchess.axl.ui.components.explore

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import memorchess.composeapp.generated.resources.Res
import memorchess.composeapp.generated.resources.explore_stat_eval
import memorchess.composeapp.generated.resources.explore_stat_pos
import memorchess.composeapp.generated.resources.explore_stat_ret
import org.jetbrains.compose.resources.stringResource
import proj.memorchess.axl.ui.theme.LocalKineticPalette
import proj.memorchess.axl.ui.theme.LocalKineticTypography

/** Row of three stat badges (eval, position count, retention) showing placeholder values. */
@Composable
fun ExploreStatBadgesRow(modifier: Modifier = Modifier) {
  Row(
    modifier = modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    ExploreStatBadge(
      label = stringResource(Res.string.explore_stat_eval),
      value = "+0.0",
      modifier = Modifier.weight(1f),
    )
    ExploreStatBadge(
      label = stringResource(Res.string.explore_stat_pos),
      value = "—",
      modifier = Modifier.weight(1f),
    )
    ExploreStatBadge(
      label = stringResource(Res.string.explore_stat_ret),
      value = "—",
      modifier = Modifier.weight(1f),
    )
  }
}

/** One stat card: an uppercase [label] over a large [value]. */
@Composable
private fun ExploreStatBadge(label: String, value: String, modifier: Modifier = Modifier) {
  val palette = LocalKineticPalette.current
  val typography = LocalKineticTypography.current
  val shape = MaterialTheme.shapes.extraSmall
  Column(
    modifier =
      modifier
        .height(44.dp)
        .clip(shape)
        .background(palette.panel, shape)
        .border(width = 1.5.dp, color = palette.line, shape = shape)
        .padding(horizontal = 12.dp),
    verticalArrangement = Arrangement.Center,
  ) {
    Text(text = label.uppercase(), style = typography.labelSm.copy(color = palette.ink3))
    Spacer(modifier = Modifier.height(2.dp))
    Text(
      text = value,
      style =
        typography.brand.copy(
          fontWeight = FontWeight.Bold,
          fontSize = 17.sp,
          color = palette.ink,
        ),
    )
  }
}
