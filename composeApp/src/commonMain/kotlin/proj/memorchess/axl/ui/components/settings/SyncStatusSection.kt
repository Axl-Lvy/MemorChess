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
import proj.memorchess.axl.core.data.online.database.isSynced

@Composable
fun SyncStatusSection(
  authManager: AuthManager = koinInject(),
  databaseSynchronizer: DatabaseSynchronizer = koinInject(),
) {
  val coroutineScope = rememberCoroutineScope()
  var lastLocalUpdate by remember { mutableStateOf(databaseSynchronizer.lastUpdates.value?.first) }
  var lastRemoteUpdate by remember {
    mutableStateOf(databaseSynchronizer.lastUpdates.value?.second)
  }
  var loading by remember { mutableStateOf(false) }
  var error by remember { mutableStateOf<String?>(null) }

  if (authManager.user != null) {
    Spacer(modifier = Modifier.height(16.dp))
    Column(
      modifier = Modifier.fillMaxWidth().padding(8.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Text("Database Synchronisation", style = MaterialTheme.typography.titleMedium)
      if (loading) {
        Text("Checking sync status...")
      } else if (error != null) {
        Text("Error: $error", color = MaterialTheme.colorScheme.error)
      } else {
        if (!isSynced) {
          Text("Local last update: " + (lastLocalUpdate?.toString() ?: "N/A"))
          Text("Remote last update: " + (lastRemoteUpdate?.toString() ?: "N/A"))
        }
        if (isSynced) {
          Text("Databases are synced", color = MaterialTheme.colorScheme.primary)
        } else {
          Text("Databases are NOT synced", color = MaterialTheme.colorScheme.error)
          val updates = databaseSynchronizer.lastUpdates.value
          lastLocalUpdate = updates?.first
          lastRemoteUpdate = updates?.second
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
              onClick = {
                loading = true
                error = null
                coroutineScope.launch {
                  try {
                    databaseSynchronizer.syncFromLocal()
                  } catch (e: Exception) {
                    error = e.message ?: "Upload failed"
                  } finally {
                    loading = false
                  }
                }
              }
            ) {
              Text("Upload Local → Remote")
            }
            Button(
              onClick = {
                loading = true
                error = null
                coroutineScope.launch {
                  try {
                    databaseSynchronizer.syncFromRemote()
                  } catch (e: Exception) {
                    error = e.message ?: "Download failed"
                  } finally {
                    loading = false
                  }
                }
              }
            ) {
              Text("Download Remote → Local")
            }
          }
        }
      }
    }
  }
}
