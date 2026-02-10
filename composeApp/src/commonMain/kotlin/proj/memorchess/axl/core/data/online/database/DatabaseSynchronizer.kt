package proj.memorchess.axl.core.data.online.database

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.time.Instant
import proj.memorchess.axl.core.data.DatabaseQueryManager
import proj.memorchess.axl.core.data.online.auth.AuthEvent
import proj.memorchess.axl.core.data.online.auth.KtorAuthManager
import proj.memorchess.axl.core.date.DateUtil.isAlmostEqual

/**
 * Class that manages the remote database, and help linking its structure with the local one.
 *
 * @property authManager Authentication manager
 * @property remoteDatabase Remote database
 * @property localDatabase Local database
 */
class DatabaseSynchronizer(
  private val authManager: KtorAuthManager,
  private val remoteDatabase: KtorQueryManager,
  private val localDatabase: DatabaseQueryManager,
) {

  /** Synced state of the database */
  var isSynced by mutableStateOf(false)
    private set

  val lastUpdates = mutableStateOf<Pair<Instant?, Instant?>?>(null)

  init {
    if (!localDatabase.isActive()) {
      isSynced = true
    }
    authManager.registerListener {
      if (it is AuthEvent.Authenticated) {
        lastUpdates.value = getLastUpdates() ?: Pair(null, null)
      }
    }
  }

  /**
   * Retrieve the last updates date from local and remote database.
   *
   * @return Pair(local, remote)
   */
  private suspend fun getLastUpdates(): Pair<Instant?, Instant?>? {
    if (authManager.user == null) {
      return null
    }
    if (!localDatabase.isActive()) {
      isSynced = true
      return Pair(null, remoteDatabase.getLastUpdate())
    }
    val lastLocalUpdate = localDatabase.getLastUpdate()

    try {
      val lastRemoteUpdate = remoteDatabase.getLastUpdate()
      isSynced = lastLocalUpdate?.isAlmostEqual(lastRemoteUpdate) ?: false
      return Pair(lastLocalUpdate, lastRemoteUpdate)
    } catch (e: IllegalStateException) {
      // If the user is not connected, we cannot retrieve the remote last update.
      return Pair(lastLocalUpdate, null)
    }
  }

  /** Uploads the database from local and overrides the remote one */
  suspend fun syncFromLocal() {
    val hardDeleteDate = lastUpdates.value?.first
    remoteDatabase.deleteAll(hardDeleteDate)
    val moves = localDatabase.getAllMoves()
    val positions = localDatabase.getAllPositions()
    remoteDatabase.insertMoves(moves, positions)
    isSynced = true
  }

  /** Download the database from remote and overrides the local one. */
  suspend fun syncFromRemote() {
    val hardDeleteDate = lastUpdates.value?.second
    localDatabase.deleteAll(hardDeleteDate)
    val moves = remoteDatabase.getAllMoves()
    val positions = remoteDatabase.getAllPositions()
    localDatabase.insertMoves(moves, positions)
    isSynced = true
  }
}
