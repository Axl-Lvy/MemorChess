package proj.memorchess.axl.ui.components.explore

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import memorchess.composeapp.generated.resources.Res
import memorchess.composeapp.generated.resources.description_board_next_move
import memorchess.composeapp.generated.resources.explore_no_continuations
import memorchess.composeapp.generated.resources.explore_notes_coming_soon
import memorchess.composeapp.generated.resources.explore_tab_continuations
import memorchess.composeapp.generated.resources.explore_tab_lichess
import memorchess.composeapp.generated.resources.explore_tab_notes
import memorchess.composeapp.generated.resources.repertoire_view_yours
import org.jetbrains.compose.resources.stringResource
import proj.memorchess.axl.core.data.explorer.ExplorerViewModel
import proj.memorchess.axl.ui.components.controls.KineticSegmentedControl
import proj.memorchess.axl.ui.theme.LocalKineticPalette
import proj.memorchess.axl.ui.theme.LocalKineticTypography

/** Tabs shown in the Explore side info panel. */
enum class ExploreInfoTab {
  CONTINUATIONS,
  LICHESS,
  NOTES,
}

/**
 * Explore info panel with Continuations, Lichess explorer, and Notes tabs. The caller's [modifier]
 * sets its footprint.
 *
 * @param isMine whether a SAN continuation is already a good move in the viewer's repertoire, which
 *   tags it YOURS; `null` disables the tagging.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExploreSidebar(
  nextMoves: List<String>,
  onPlayMove: (String) -> Unit,
  explorerViewModel: ExplorerViewModel,
  onPlayLichessMove: (String) -> Unit,
  isMine: ((String) -> Boolean)? = null,
  modifier: Modifier = Modifier,
) {
  val palette = LocalKineticPalette.current
  val typography = LocalKineticTypography.current
  var selected by remember { mutableStateOf(ExploreInfoTab.CONTINUATIONS) }
  val continuationsLabel = stringResource(Res.string.explore_tab_continuations)
  val lichessLabel = stringResource(Res.string.explore_tab_lichess)
  val notesLabel = stringResource(Res.string.explore_tab_notes)
  val panelShape = MaterialTheme.shapes.medium

  // No clip needed: the padding after the border keeps every child off the rounded corners.
  Column(
    modifier =
      modifier
        .background(palette.panel, panelShape)
        .border(width = 1.5.dp, color = palette.line, shape = panelShape)
        .padding(12.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    KineticSegmentedControl(
      options = ExploreInfoTab.entries.toList(),
      selected = selected,
      onSelect = { selected = it },
      modifier = Modifier.fillMaxWidth(),
      label = {
        when (it) {
          ExploreInfoTab.CONTINUATIONS -> continuationsLabel
          ExploreInfoTab.LICHESS -> lichessLabel
          ExploreInfoTab.NOTES -> notesLabel
        }
      },
    )

    when (selected) {
      ExploreInfoTab.CONTINUATIONS ->
        ContinuationsContent(nextMoves = nextMoves, onPlay = onPlayMove, isMine = isMine)
      ExploreInfoTab.LICHESS ->
        LichessExplorerPanel(
          viewModel = explorerViewModel,
          onClickMove = onPlayLichessMove,
          modifier = Modifier.fillMaxWidth(),
        )
      ExploreInfoTab.NOTES ->
        Box(
          modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp),
          contentAlignment = Alignment.Center,
        ) {
          Text(
            text = stringResource(Res.string.explore_notes_coming_soon),
            style = typography.labelSm.copy(color = palette.ink3),
          )
        }
    }
  }
}

/** Continuations tab: a flowing row of SAN pills. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ContinuationsContent(
  nextMoves: List<String>,
  onPlay: (String) -> Unit,
  isMine: ((String) -> Boolean)? = null,
) {
  val palette = LocalKineticPalette.current
  val typography = LocalKineticTypography.current
  val chipShape = MaterialTheme.shapes.extraSmall

  if (nextMoves.isEmpty()) {
    Box(
      modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp),
      contentAlignment = Alignment.Center,
    ) {
      Text(
        text = stringResource(Res.string.explore_no_continuations),
        style = typography.labelSm.copy(color = palette.ink3),
      )
    }
    return
  }

  FlowRow(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    nextMoves.forEach { san ->
      // The clip must precede `clickable` or the ripple squares off the corners. `clickable` and
      // `testTag` stay adjacent so the click and the tag share one semantics node.
      Box(
        modifier =
          Modifier.background(palette.panel2, chipShape)
            .border(width = 1.5.dp, color = palette.line, shape = chipShape)
            .clip(chipShape)
            .clickable { onPlay(san) }
            .testTag(stringResource(Res.string.description_board_next_move, san))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
      ) {
        if (isMine?.invoke(san) == true) {
          Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(text = san, style = typography.mono.copy(color = palette.ink))
            Text(
              text = stringResource(Res.string.repertoire_view_yours),
              style =
                typography.labelSm.copy(
                  fontSize = 7.5.sp,
                  letterSpacing = 0.08.em,
                  color = palette.onProgress,
                ),
              modifier =
                Modifier.background(palette.progress, RoundedCornerShape(7.dp))
                  .padding(horizontal = 5.dp, vertical = 2.dp),
            )
          }
        } else {
          Text(text = san, style = typography.mono.copy(color = palette.ink))
        }
      }
    }
  }
}
