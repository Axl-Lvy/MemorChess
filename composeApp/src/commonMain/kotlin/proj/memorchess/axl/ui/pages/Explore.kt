package proj.memorchess.axl.ui.pages

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import co.touchlab.kermit.Logger
import compose.icons.FeatherIcons
import compose.icons.feathericons.Save
import compose.icons.feathericons.Trash
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import proj.memorchess.axl.core.data.PositionKey
import proj.memorchess.axl.core.graph.TreeRepository
import proj.memorchess.axl.core.graph.nodes.NodeManager
import proj.memorchess.axl.core.interactions.LinesExplorer
import proj.memorchess.axl.ui.components.loading.LoadingWidget
import proj.memorchess.axl.ui.components.popup.ConfirmationDialog
import proj.memorchess.axl.ui.pages.navigation.Route

private val LOGGER = Logger.withTag("Explore")

@Composable
fun Explore(
  position: PositionKey? = null,
  nodeManager: NodeManager = koinInject(),
  treeRepository: TreeRepository = koinInject(),
) {
  Column(
    modifier =
      Modifier.fillMaxSize()
        .padding(horizontal = 2.dp, vertical = 8.dp)
        .testTag(Route.ExploreRoute.DEFAULT.getLabel()),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    LoadingWidget({ nodeManager.resetCacheFromSource() }) {
      val initialPosition = extractInitialPosition(position, nodeManager)
      val linesExplorer = remember { LinesExplorer(initialPosition, nodeManager, treeRepository) }
      val coroutineScope = rememberCoroutineScope()

      val deletionConfirmationDialog = remember { ConfirmationDialog(okText = "Delete") }
      deletionConfirmationDialog.DrawDialog()

      ExplorerContent(
        explorer = linesExplorer,
        saveButton = {
          Button(
            onClick = { coroutineScope.launch { linesExplorer.save() } },
            it,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
          ) {
            Icon(FeatherIcons.Save, contentDescription = "Save")
          }
        },
        deleteButton = {
          Button(
            onClick = {
              deletionConfirmationDialog.show(
                confirm = { coroutineScope.launch { linesExplorer.delete() } }
              ) {
                var nodesToDelete by remember { mutableStateOf<Int?>(null) }
                if (nodesToDelete == null) {
                  CircularProgressIndicator()
                } else {
                  val finalNodesToDelete = nodesToDelete ?: 0
                  Text(
                    "Are you sure you want to delete $finalNodesToDelete position${if (finalNodesToDelete > 1) "s" else ""}?"
                  )
                }
                LaunchedEffect(nodesToDelete) {
                  nodesToDelete = linesExplorer.calculateNumberOfNodeToDelete()
                }
              }
            },
            it,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
          ) {
            Icon(FeatherIcons.Trash, contentDescription = "Delete")
          }
        },
      )
    }
  }
}

private fun extractInitialPosition(position: PositionKey?, nodeManager: NodeManager): PositionKey? {
  return if (position == null) {
    null
  } else if (!nodeManager.isKnown(position)) {
    LOGGER.w {
      "Position $position is not stored yet. You must first store it to integrate it in your position tree."
    }
    null
  } else {
    position
  }
}
