package proj.memorchess.axl.core.data.online.database

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/**
 * Handles asynchronous upload of database operations to the remote database.
 *
 * Operations are queued and processed sequentially in the background.
 * This decouples local database operations from remote synchronization,
 * improving responsiveness and handling network issues gracefully.
 *
 * @property remoteDatabase The remote database to upload operations to.
 * @property databaseSynchronizer The synchronizer to check sync status.
 */
class DatabaseUploader(
  private val remoteDatabase: SupabaseQueryManager,
  private val databaseSynchronizer: DatabaseSynchronizer,
) {

  private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
  private val operationChannel = Channel<DatabaseOperation>(Channel.UNLIMITED)

  /** Number of pending operations in the queue. */
  var pendingOperationsCount by mutableStateOf(0)
    private set

  /** Whether the uploader is currently processing an operation. */
  var isProcessing by mutableStateOf(false)
    private set

  /** Last error encountered during upload, null if no error. */
  var lastError by mutableStateOf<Throwable?>(null)
    private set

  /** Whether the databases are synchronized. */
  val isSynced: Boolean
    get() = databaseSynchronizer.isSynced

  /** Whether the uploader is idle (no pending operations). */
  val isIdle: Boolean
    get() = pendingOperationsCount == 0 && !isProcessing

  init {
    scope.launch {
      processQueue()
    }
  }

  /**
   * Enqueues a database operation for upload to the remote database.
   *
   * The operation will be processed asynchronously if the remote database is active
   * and synchronized.
   *
   * @param operation The operation to enqueue.
   */
  fun enqueue(operation: DatabaseOperation) {
    if (!remoteDatabase.isActive() || !databaseSynchronizer.isSynced) {
      LOGGER.d { "Skipping enqueue: remote not active or not synced" }
      return
    }
    pendingOperationsCount++
    scope.launch {
      operationChannel.send(operation)
    }
  }

  private suspend fun processQueue() {
    for (operation in operationChannel) {
      isProcessing = true
      try {
        executeOperation(operation)
        lastError = null
      } catch (e: Exception) {
        LOGGER.e(e) { "Failed to execute operation: $operation" }
        lastError = e
      } finally {
        pendingOperationsCount--
        isProcessing = pendingOperationsCount > 0
      }
    }
  }

  private suspend fun executeOperation(operation: DatabaseOperation) {
    LOGGER.d { "Executing operation: $operation" }
    when (operation) {
      is DatabaseOperation.InsertNodes -> {
        remoteDatabase.insertNodes(*operation.nodes.toTypedArray())
      }
      is DatabaseOperation.DeletePosition -> {
        remoteDatabase.deletePosition(operation.position)
      }
      is DatabaseOperation.DeleteMove -> {
        remoteDatabase.deleteMove(operation.origin, operation.move)
      }
      is DatabaseOperation.DeleteAll -> {
        remoteDatabase.deleteAll(operation.hardFrom)
      }
    }
    LOGGER.d { "Operation completed: $operation" }
  }
}

private val LOGGER = Logger.withTag("DatabaseUploader")

