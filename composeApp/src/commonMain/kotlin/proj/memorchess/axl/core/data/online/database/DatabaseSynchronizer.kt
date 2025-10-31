package proj.memorchess.axl.core.data.online.database

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import co.touchlab.kermit.Logger
import io.github.jan.supabase.auth.status.SessionStatus
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import proj.memorchess.axl.core.config.LAST_SYNC
import proj.memorchess.axl.core.data.DatabaseQueryManager
import proj.memorchess.axl.core.data.online.auth.AuthManager
import proj.memorchess.axl.core.date.DateUtil

/**
 * Class that manages the remote database, and help linking its structure with the local one.
 *
 * @property authManager Authentication manager
 * @property remoteDatabase Remote database
 * @property localDatabase Local database
 * @property cloudUploader Cloud uploader for background uploads
 */
class DatabaseSynchronizer(
  private val authManager: AuthManager,
  private val remoteDatabase: SupabaseQueryManager,
  private val localDatabase: DatabaseQueryManager,
  private val cloudUploader: CloudUploader,
) {

  private val syncScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

  /** Synced state of the database */
  var isSynced by mutableStateOf(false)
    private set

  val lastUpdates = mutableStateOf<Pair<Instant?, Instant?>?>(null)

  init {
    if (!localDatabase.isActive()) {
      isSynced = true
    }
    authManager.registerListener {
      when (it) {
        is SessionStatus.Authenticated -> {
          syncScope.launch { performIncrementalSync() }
        }
        is SessionStatus.NotAuthenticated -> {
          if (it.isSignOut) {
            isSynced = false
            LOGGER.i { "User signed out, marking as not synced" }
          }
        }
        else -> {}
      }
    }
  }

  /**
   * Performs an incremental sync on sign-in:
   * 1. Downloads new cloud data (updated after LAST_SYNC)
   * 2. Uploads new local data (updated after LAST_SYNC)
   */
  private suspend fun performIncrementalSync() {
    if (authManager.user == null) {
      LOGGER.w { "Cannot sync: user not authenticated" }
      return
    }

    if (!localDatabase.isActive()) {
      isSynced = true
      LOGGER.i { "Local database not active, skipping sync" }
      return
    }

    try {
      val lastSync = LAST_SYNC.getValue()
      LOGGER.i { "Starting incremental sync. Last sync: $lastSync" }

      // Step 1: Download new cloud data
      LOGGER.i { "Downloading new cloud data..." }
      val allRemoteNodes = remoteDatabase.getAllNodes()
      val newRemoteNodes = allRemoteNodes.filter { it.updatedAt > lastSync }

      if (newRemoteNodes.isNotEmpty()) {
        LOGGER.i { "Downloading ${newRemoteNodes.size} new/updated nodes from cloud" }
        localDatabase.insertNodes(*newRemoteNodes.toTypedArray())
      } else {
        LOGGER.i { "No new cloud data to download" }
      }

      // Step 2: Upload new local data
      LOGGER.i { "Uploading new local data..." }
      cloudUploader.uploadNewDataToCloud()

      // Update last sync timestamp
      val now = DateUtil.now()
      LAST_SYNC.setValue(now)
      LOGGER.i { "Incremental sync completed. New LAST_SYNC: $now" }

      isSynced = true
      lastUpdates.value = Pair(now, now)
    } catch (e: Exception) {
      LOGGER.e(e) { "Failed to perform incremental sync" }
      isSynced = false
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
      return Pair(lastLocalUpdate, lastRemoteUpdate)
    } catch (e: IllegalStateException) {
      // If the user is not connected, we cannot retrieve the remote last update.
      return Pair(lastLocalUpdate, null)
    }
  }

  /** Uploads the database from local and overrides the remote one */
  suspend fun syncFromLocal() {
    val updates = getLastUpdates()
    lastUpdates.value = updates ?: Pair(null, null)
    val hardDeleteDate = updates?.first
    remoteDatabase.deleteAll(hardDeleteDate)
    remoteDatabase.insertNodes(*localDatabase.getAllNodes().toTypedArray())
    val now = DateUtil.now()
    LAST_SYNC.setValue(now)
    isSynced = true
  }

  /** Download the database from remote and overrides the local one. */
  suspend fun syncFromRemote() {
    val updates = getLastUpdates()
    lastUpdates.value = updates ?: Pair(null, null)
    val hardDeleteDate = updates?.second
    localDatabase.deleteAll(hardDeleteDate)
    val positionToStoredNode = remoteDatabase.getAllNodes()
    localDatabase.insertNodes(*positionToStoredNode.toTypedArray())
    val now = DateUtil.now()
    LAST_SYNC.setValue(now)
    isSynced = true
  }
}

private val LOGGER = Logger.withTag("DatabaseSynchronizer")
