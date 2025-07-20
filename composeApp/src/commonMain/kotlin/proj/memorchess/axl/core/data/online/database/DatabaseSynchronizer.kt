package proj.memorchess.axl.core.data.online.database

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.datetime.LocalDateTime
import proj.memorchess.axl.core.data.DatabaseQueryManager
import proj.memorchess.axl.core.data.online.auth.AuthManager

/**
 * Class that manages the remote database, and help linking its structure with the local one.
 *
 * @property authManager Authentication manager
 * @property remoteDatabase Remote database
 * @property localDatabase Local database
 */
class DatabaseSynchronizer(
  private val authManager: AuthManager,
  private val remoteDatabase: RemoteDatabaseQueryManager,
  private val localDatabase: DatabaseQueryManager,
) {

  val lastUpdates = mutableStateOf<Pair<LocalDateTime?, LocalDateTime?>?>(null)

  init {
    authManager.registerListener {
      if (it is SessionStatus.Authenticated) {
        lastUpdates.value = getLastUpdates() ?: Pair(null, null)
      }
    }
  }

  /**
   * Retrieve the last updates date from local and remote database.
   *
   * @return Pair(local, remote)
   */
  private suspend fun getLastUpdates(): Pair<LocalDateTime?, LocalDateTime?>? {
    if (authManager.user == null) {
      return null
    }
    val lastLocalUpdate = localDatabase.getLastUpdate()

    val lastRemoteUpdate = remoteDatabase.getLastUpdate()
    isSynced = lastLocalUpdate == lastRemoteUpdate
    return Pair(lastLocalUpdate, lastRemoteUpdate)
  }

  /** Uploads the database from local and overrides the remote one */
  suspend fun syncFromLocal() {
    val hardDeleteDate = lastUpdates.value?.first
    remoteDatabase.deleteAll(hardDeleteDate)
    uploadNodes()
    uploadMoves()
    isSynced = true
  }

  private suspend fun uploadNodes() {
    val localNodesToUpload = localDatabase.getAllPositions()
    remoteDatabase.insertUnlinkedStoredNodes(localNodesToUpload)
  }

  private suspend fun uploadMoves() {
    val localMovesToUpload = localDatabase.getAllMoves()
    remoteDatabase.insertMoves(localMovesToUpload)
  }

  /** Download the database from remote and overrides the local one. */
  suspend fun syncFromRemote() {
    val hardDeleteDate = lastUpdates.value?.second
    localDatabase.deleteAll(hardDeleteDate)
    val positionToStoredNode = remoteDatabase.getAllNodes()
    positionToStoredNode.forEach { localDatabase.insertPosition(it) }
    isSynced = true
  }
}

/** Synced state of the database */
var isSynced by mutableStateOf(false)
  private set
