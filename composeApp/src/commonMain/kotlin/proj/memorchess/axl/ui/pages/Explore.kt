package proj.memorchess.axl.ui.pages

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
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
import org.koin.compose.koinInject
import proj.memorchess.axl.core.data.PositionKey
import proj.memorchess.axl.core.data.PruneImpact
import proj.memorchess.axl.core.data.explorer.CachedExplorer
import proj.memorchess.axl.core.graph.RepertoireTagStore
import proj.memorchess.axl.core.graph.TreeStore
import proj.memorchess.axl.core.interactions.LinesExplorer
import proj.memorchess.axl.ui.components.explore.ExplorePruneSheet
import proj.memorchess.axl.ui.components.explore.PruneStep
import proj.memorchess.axl.ui.components.loading.LoadingWidget
import proj.memorchess.axl.ui.pages.navigation.Route

private val LOGGER = Logger.withTag("Explore")

/**
 * Free exploration page. Lets the user wander the opening graph, save lines as good, or prune
 * subtrees in two steps: Delete highlights the branch behind a scrim, then a sheet confirms the
 * loss.
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
  tagStore: RepertoireTagStore = koinInject(),
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
        remember(repertoireId) { LinesExplorer(initialPosition, treeStore, tagStore, repertoireId) }
      LaunchedEffect(linesExplorer) { linesExplorer.initState() }
      val coroutineScope = rememberCoroutineScope()
      val explorerViewModel = rememberExplorerViewModel(linesExplorer, cachedExplorer)

      // The step outlives the sheet's visibility so the content holds still while it slides out.
      var pruneOpen by remember { mutableStateOf(false) }
      var pruneStep by remember { mutableStateOf(PruneStep.SELECTING) }
      var pruneImpact by remember { mutableStateOf<PruneImpact?>(null) }
      LaunchedEffect(pruneOpen) {
        if (pruneOpen) {
          // Reset so the spinner shows again on reopen, not a stale impact from last time.
          pruneImpact = null
          pruneImpact = linesExplorer.calculatePruneImpact()
        }
      }

      ExplorePruneSheet(
        visible = pruneOpen,
        step = pruneStep,
        impact = pruneImpact,
        onContinue = { pruneStep = PruneStep.CONFIRMING },
        onConfirm = {
          pruneOpen = false
          coroutineScope.launch { linesExplorer.delete() }
        },
        onDismiss = { pruneOpen = false },
      )

      ExplorerContent(
        explorer = linesExplorer,
        explorerViewModel = explorerViewModel,
        onSave = { coroutineScope.launch { linesExplorer.save() } },
        onDelete = {
          pruneStep = PruneStep.SELECTING
          pruneOpen = true
        },
        markedForPruning = pruneOpen,
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
