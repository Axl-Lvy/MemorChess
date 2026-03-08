package proj.memorchess.axl.ui.components.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import proj.memorchess.axl.core.data.online.auth.AuthManager
import proj.memorchess.axl.core.data.online.database.DatabaseSynchronizer

@Composable
fun SyncStatusSection(
  authManager: AuthManager = koinInject(),
  databaseSynchronizer: DatabaseSynchronizer = koinInject(),
) {
  if (authManager.user == null) return

  val coroutineScope = rememberCoroutineScope()
  var loading by remember { mutableStateOf(false) }
  var error by remember { mutableStateOf<String?>(null) }

  val updates = databaseSynchronizer.lastUpdates.value
  val lastLocalUpdate = updates?.first
  val lastRemoteUpdate = updates?.second

  SyncStatusContent(
    loading = loading,
    error = error,
    isSynced = databaseSynchronizer.isSynced,
    lastLocalUpdate = lastLocalUpdate,
    lastRemoteUpdate = lastRemoteUpdate,
    onSyncFromLocal = {
      loading = true // NOSONAR: Compose state triggers recomposition
      error = null // NOSONAR: Compose state triggers recomposition
      coroutineScope.launch {
        try {
          databaseSynchronizer.syncFromLocal()
        } catch (e: Exception) {
          error = e.message ?: "Upload failed" // NOSONAR: Compose state triggers recomposition
        } finally {
          loading = false // NOSONAR: Compose state triggers recomposition
        }
      }
    },
    onSyncFromRemote = {
      loading = true // NOSONAR: Compose state triggers recomposition
      error = null // NOSONAR: Compose state triggers recomposition
      coroutineScope.launch {
        try {
          databaseSynchronizer.syncFromRemote()
        } catch (e: Exception) {
          error = e.message ?: "Download failed" // NOSONAR: Compose state triggers recomposition
        } finally {
          loading = false // NOSONAR: Compose state triggers recomposition
        }
      }
    },
  )
}

@Composable
private fun SyncStatusContent(
  loading: Boolean,
  error: String?,
  isSynced: Boolean,
  lastLocalUpdate: Any?,
  lastRemoteUpdate: Any?,
  onSyncFromLocal: () -> Unit,
  onSyncFromRemote: () -> Unit,
) {
  Spacer(modifier = Modifier.height(16.dp))
  Column(
    modifier = Modifier.fillMaxWidth().padding(8.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Text("Database Synchronisation", style = MaterialTheme.typography.titleMedium)

    when {
      loading -> Text("Checking sync status...")
      error != null -> Text("Error: $error", color = MaterialTheme.colorScheme.error)
      else ->
        SyncStatusDetails(
          isSynced = isSynced,
          lastLocalUpdate = lastLocalUpdate,
          lastRemoteUpdate = lastRemoteUpdate,
          onSyncFromLocal = onSyncFromLocal,
          onSyncFromRemote = onSyncFromRemote,
        )
    }
  }
}

@Composable
private fun SyncStatusDetails(
  isSynced: Boolean,
  lastLocalUpdate: Any?,
  lastRemoteUpdate: Any?,
  onSyncFromLocal: () -> Unit,
  onSyncFromRemote: () -> Unit,
) {
  if (isSynced) {
    Text("Databases are synced", color = MaterialTheme.colorScheme.primary)
  } else {
    Text("Local last update: ${lastLocalUpdate?.toString() ?: "N/A"}")
    Text("Remote last update: ${lastRemoteUpdate?.toString() ?: "N/A"}")
    Text("Databases are NOT synced", color = MaterialTheme.colorScheme.error)
    SyncActions(onSyncFromLocal, onSyncFromRemote)
  }
}

@Composable
private fun SyncActions(onSyncFromLocal: () -> Unit, onSyncFromRemote: () -> Unit) {
  Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    Button(onClick = onSyncFromLocal) { Text("Upload Local → Remote") }
    Button(onClick = onSyncFromRemote) { Text("Download Remote → Local") }
  }
}
