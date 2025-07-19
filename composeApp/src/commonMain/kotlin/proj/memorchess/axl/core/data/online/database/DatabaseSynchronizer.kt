package proj.memorchess.axl.core.data.online.database

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.datetime.LocalDateTime
import proj.memorchess.axl.core.data.DatabaseQueryManager
import proj.memorchess.axl.core.data.StoredNode
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

  /**
   * Retrieve the last updates date from local and remote database.
   *
   * @return Pair(local, remote)
   */
  suspend fun getLastUpdates(): Pair<LocalDateTime?, LocalDateTime?>? {
    if (authManager.user == null) {
      return null
    }
    val lastLocalMoveUpdate = localDatabase.getLastMoveUpdate()

    val lastRemoteMoveUpdate = remoteDatabase.getLastMoveUpdate()
    isSynced = lastLocalMoveUpdate == lastRemoteMoveUpdate
    return Pair(lastLocalMoveUpdate, lastRemoteMoveUpdate)
  }

  /**
   * Saves a [StoredNode] to the remote database
   *
   * @param storedNode The node to upload
   */
  suspend fun saveStoredNode(storedNode: StoredNode) {
    if (authManager.user == null) {
      return
    }
    remoteDatabase.insertPosition(storedNode)
  }

  /** Uploads the database from local and overrides the remote one */
  suspend fun syncFromLocal() {
    remoteDatabase.deleteAll()
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
    localDatabase.deleteAll()
    val positionToStoredNode = remoteDatabase.getAllNodes()
    positionToStoredNode.forEach { localDatabase.insertPosition(it) }
    isSynced = true
  }
}

/** Synced state of the database */
var isSynced by mutableStateOf(false)
  private set
