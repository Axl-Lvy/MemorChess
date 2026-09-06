package proj.memorchess.axl.ui.pages

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import co.touchlab.kermit.Logger
import kotlinx.coroutines.launch
import memorchess.composeapp.generated.resources.Res
import memorchess.composeapp.generated.resources.dialog_cancel
import memorchess.composeapp.generated.resources.dialog_delete
import memorchess.composeapp.generated.resources.explore_delete_confirm
import memorchess.composeapp.generated.resources.explore_delete_confirm_many
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import proj.memorchess.axl.core.data.DESCENDANT_COUNT_CAP
import proj.memorchess.axl.core.data.PositionKey
import proj.memorchess.axl.core.data.explorer.CachedExplorer
import proj.memorchess.axl.core.graph.TreeStore
import proj.memorchess.axl.core.interactions.LinesExplorer
import proj.memorchess.axl.ui.components.loading.LoadingWidget
import proj.memorchess.axl.ui.components.popup.KineticBottomSheet
import proj.memorchess.axl.ui.pages.navigation.Route

private val LOGGER = Logger.withTag("Explore")

/**
 * Free exploration page. Lets the user wander the opening graph, save lines as good, or prune
 * subtrees.
 *
 * The persisted graph is demand paged, so nothing is preloaded: the loading phase only resolves
 * whether the requested [position] is stored (a single point lookup), then the board reads each
 * position on demand through [TreeStore.node]. [repertoireId] narrows the session to one
 * repertoire, or `null` for the unscoped default.
 */
@Composable
fun Explore(
  position: PositionKey? = null,
  repertoireId: String? = null,
  treeStore: TreeStore = koinInject(),
  cachedExplorer: CachedExplorer = koinInject(),
) {
  Column(
    modifier = Modifier.fillMaxSize().testTag(Route.ExploreRoute.DEFAULT.getLabel()),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    // Resolved during the loading phase: the starting position to open on, or null to start from
    // the root when the requested position is not stored yet.
    var initialPosition by remember { mutableStateOf<PositionKey?>(null) }
    LoadingWidget({ initialPosition = extractInitialPosition(position, treeStore) }) {
      val linesExplorer =
        remember(repertoireId) { LinesExplorer(initialPosition, treeStore, repertoireId) }
      LaunchedEffect(linesExplorer) { linesExplorer.initState() }
      val coroutineScope = rememberCoroutineScope()
      val explorerViewModel = rememberExplorerViewModel(linesExplorer, cachedExplorer)

      var showDeleteSheet by remember { mutableStateOf(false) }

      KineticBottomSheet(
        visible = showDeleteSheet,
        onDismissRequest = { showDeleteSheet = false },
        modifier = Modifier.testTag("pruneConfirmSheet"),
        buttons = {
          TextButton(
            modifier = Modifier.testTag("pruneConfirmSheetCancelButton"),
            onClick = { showDeleteSheet = false },
          ) {
            Text(stringResource(Res.string.dialog_cancel))
          }
          TextButton(
            modifier = Modifier.testTag("pruneConfirmSheetOkButton"),
            onClick = {
              showDeleteSheet = false
              coroutineScope.launch { linesExplorer.delete() }
            },
          ) {
            Text(stringResource(Res.string.dialog_delete))
          }
        },
      ) {
        var nodesToDelete by remember { mutableStateOf<Int?>(null) }
        val finalNodesToDelete = nodesToDelete
        if (finalNodesToDelete == null) {
          CircularProgressIndicator()
        } else if (finalNodesToDelete >= DESCENDANT_COUNT_CAP) {
          // The count is capped DB-side, so at the cap we only know "this many or more".
          Text(stringResource(Res.string.explore_delete_confirm_many, DESCENDANT_COUNT_CAP))
        } else {
          Text(
            pluralStringResource(
              Res.plurals.explore_delete_confirm,
              finalNodesToDelete,
              finalNodesToDelete,
            )
          )
        }
        LaunchedEffect(showDeleteSheet) {
          if (showDeleteSheet) {
            // Reset so the spinner shows again on reopen, not a stale count from last time.
            nodesToDelete = null
            nodesToDelete = linesExplorer.calculateNumberOfNodeToDelete()
          }
        }
      }

      ExplorerContent(
        explorer = linesExplorer,
        explorerViewModel = explorerViewModel,
        onSave = { coroutineScope.launch { linesExplorer.save() } },
        onDelete = { showDeleteSheet = true },
      )
    }
  }
}

private suspend fun extractInitialPosition(
  position: PositionKey?,
  treeStore: TreeStore,
): PositionKey? {
  return if (position == null) {
    null
  } else if (treeStore.node(position) == null) {
    LOGGER.w {
      "Position $position is not stored yet. You must first store it to integrate it in your position tree."
    }
    null
  } else {
    position
  }
}
