package proj.memorchess.axl.ui.components.explore

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import memorchess.composeapp.generated.resources.Res
import memorchess.composeapp.generated.resources.explore_source_lichess
import memorchess.composeapp.generated.resources.explore_source_masters
import memorchess.composeapp.generated.resources.explore_stats_description
import memorchess.composeapp.generated.resources.explore_stats_description_empty
import memorchess.composeapp.generated.resources.explore_stats_label
import memorchess.composeapp.generated.resources.explore_stats_no_percents
import memorchess.composeapp.generated.resources.explore_stats_percents
import memorchess.composeapp.generated.resources.explore_stats_source_lichess
import memorchess.composeapp.generated.resources.explore_stats_source_masters
import memorchess.composeapp.generated.resources.explore_stats_updating
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import proj.memorchess.axl.core.data.explorer.ExplorerSource
import proj.memorchess.axl.core.data.explorer.ExplorerViewModel
import proj.memorchess.axl.core.data.explorer.OutcomePercents
import proj.memorchess.axl.core.data.explorer.PositionStatsState
import proj.memorchess.axl.core.data.explorer.shareLabel
import proj.memorchess.axl.core.data.explorer.toCompactCount
import proj.memorchess.axl.ui.theme.LocalKineticPalette
import proj.memorchess.axl.ui.theme.LocalKineticTypography

private const val OUTDATED_ALPHA = 0.45f

/**
 * Outcome shares and game count for the current position, drawn from [viewModel]. Shows nothing
 * until a first response has loaded.
 */
@Composable
internal fun PositionStatsBar(viewModel: ExplorerViewModel, modifier: Modifier = Modifier) {
  val state by viewModel.positionStats.collectAsState()
  PositionStatsBarContent(state = state, modifier = modifier)
}

/** Stateless rendering of [PositionStatsBar]; dims and keeps its size while [state] is outdated. */
@Composable
internal fun PositionStatsBarContent(state: PositionStatsState, modifier: Modifier = Modifier) {
  val shown = state as? PositionStatsState.Shown ?: return
  val palette = LocalKineticPalette.current
  val typography = LocalKineticTypography.current
  val response = shown.response
  val count = response.totalGames.toCompactCount()
  val percents = OutcomePercents.of(response.white, response.draws, response.black)
  val label = stringResource(Res.string.explore_stats_label, sourceLabel(shown.source), count)
  val percentsText =
    if (percents == null) stringResource(Res.string.explore_stats_no_percents)
    else
      stringResource(
        Res.string.explore_stats_percents,
        shareLabel(response.white, percents.white),
        shareLabel(response.draws, percents.draws),
        shareLabel(response.black, percents.black),
      )
  val description = description(shown, percents)
  val updating = stringResource(Res.string.explore_stats_updating)

  Column(
    modifier =
      modifier
        .fillMaxWidth()
        .testTag("position_stats_bar")
        .semantics(mergeDescendants = true) {
          contentDescription = description
          if (!shown.isCurrent) stateDescription = updating
        }
        .alpha(if (shown.isCurrent) 1f else OUTDATED_ALPHA),
    verticalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      // The label gives way first so the percentages are never cut.
      Text(
        text = label,
        style = typography.labelSm.copy(color = palette.ink2),
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f),
      )
      Text(
        text = percentsText,
        style = typography.labelSm.copy(color = palette.ink3),
        maxLines = 1,
        softWrap = false,
      )
    }
    OutcomeBar(
      white = response.white,
      draws = response.draws,
      black = response.black,
      modifier = Modifier.fillMaxWidth().height(6.dp),
    )
  }
}

@Composable
private fun sourceLabel(source: ExplorerSource): String =
  when (source) {
    ExplorerSource.MASTERS -> stringResource(Res.string.explore_stats_source_masters)
    ExplorerSource.LICHESS -> stringResource(Res.string.explore_stats_source_lichess)
  }

@Composable
private fun description(
  shown: PositionStatsState.Shown,
  percents: OutcomePercents?,
): String {
  val sourceName =
    when (shown.source) {
      ExplorerSource.MASTERS -> stringResource(Res.string.explore_source_masters)
      ExplorerSource.LICHESS -> stringResource(Res.string.explore_source_lichess)
    }
  if (percents == null) {
    return stringResource(Res.string.explore_stats_description_empty, sourceName)
  }
  val response = shown.response
  return pluralStringResource(
    Res.plurals.explore_stats_description,
    response.totalGames.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
    sourceName,
    // The full number: screen readers misread compact suffixes such as "M".
    response.totalGames.toString(),
    shareLabel(response.white, percents.white),
    shareLabel(response.draws, percents.draws),
    shareLabel(response.black, percents.black),
  )
}
