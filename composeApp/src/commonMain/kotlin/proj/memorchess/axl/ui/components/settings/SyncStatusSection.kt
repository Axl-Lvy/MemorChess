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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDateTime
import org.koin.compose.koinInject
import proj.memorchess.axl.core.data.online.auth.SupabaseAuthManager
import proj.memorchess.axl.core.data.online.database.RemoteDatabaseManager

@Composable
fun SyncStatusSection(
  supabaseAuthManager: SupabaseAuthManager = koinInject(),
  remoteDatabaseManager: RemoteDatabaseManager = koinInject(),
) {
  val coroutineScope = rememberCoroutineScope()
  var lastLocalUpdate by remember { mutableStateOf<LocalDateTime?>(null) }
  var lastRemoteUpdate by remember { mutableStateOf<LocalDateTime?>(null) }
  var loading by remember { mutableStateOf(false) }
  var error by remember { mutableStateOf<String?>(null) }
  var synced by remember { mutableStateOf(false) }

  LaunchedEffect(supabaseAuthManager.user) {
    if (supabaseAuthManager.user != null) {
      loading = true
      error = null
      try {
        val updates = remoteDatabaseManager.getLastUpdates()
        lastLocalUpdate = updates?.first
        lastRemoteUpdate = updates?.second
        synced = (lastLocalUpdate != null && lastLocalUpdate == lastRemoteUpdate)
      } catch (e: Exception) {
        error = e.message ?: "Failed to fetch sync status"
      } finally {
        loading = false
      }
    }
  }

  if (supabaseAuthManager.user != null) {
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
        Text("Local last update: " + (lastLocalUpdate?.toString() ?: "N/A"))
        Text("Remote last update: " + (lastRemoteUpdate?.toString() ?: "N/A"))
        if (synced) {
          Text("Databases are synced", color = MaterialTheme.colorScheme.primary)
        } else {
          Text("Databases are NOT synced", color = MaterialTheme.colorScheme.error)
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
              onClick = {
                loading = true
                error = null
                coroutineScope.launch {
                  try {
                    remoteDatabaseManager.syncFromLocal()
                    val updates = remoteDatabaseManager.getLastUpdates()
                    lastLocalUpdate = updates?.first
                    lastRemoteUpdate = updates?.second
                    synced = (lastLocalUpdate != null && lastLocalUpdate == lastRemoteUpdate)
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
                    remoteDatabaseManager.syncFromRemote()
                    val updates = remoteDatabaseManager.getLastUpdates()
                    lastLocalUpdate = updates?.first
                    lastRemoteUpdate = updates?.second
                    synced = (lastLocalUpdate != null && lastLocalUpdate == lastRemoteUpdate)
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
