package proj.memorchess.axl.core.data.online.database

import co.touchlab.kermit.Logger
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import proj.memorchess.axl.core.config.LAST_SYNC
import proj.memorchess.axl.core.data.DataNode
import proj.memorchess.axl.core.data.DatabaseQueryManager
import proj.memorchess.axl.core.data.PositionIdentifier
import proj.memorchess.axl.core.data.online.auth.AuthManager

/**
 * Handles background uploading of local database changes to the cloud.
 *
 * Operations are queued and processed in the background when the user is authenticated.
 *
 * @property authManager Authentication manager
 * @property remoteDatabase Remote database
 * @property localDatabase Local database
 */
class CloudUploader(
  private val authManager: AuthManager,
  private val remoteDatabase: SupabaseQueryManager,
  private val localDatabase: DatabaseQueryManager,
) {
  private val uploadScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

  private sealed class UploadOperation {
    data class InsertNodes(val nodes: List<DataNode>) : UploadOperation()

    data class DeletePosition(val position: PositionIdentifier) : UploadOperation()

    data class DeleteMove(val origin: PositionIdentifier, val move: String) : UploadOperation()

    data class DeleteAll(val hardFrom: Instant?) : UploadOperation()
  }

  private val operationQueue = Channel<UploadOperation>(Channel.UNLIMITED)

  init {
    uploadScope.launch {
      for (operation in operationQueue) {
        processOperation(operation)
      }
    }
  }

  private suspend fun processOperation(operation: UploadOperation) {
    // Wait until user is authenticated
    while (authManager.user == null) {
      delay(1000)
    }

    try {
      when (operation) {
        is UploadOperation.InsertNodes -> {
          LOGGER.i { "Uploading ${operation.nodes.size} nodes to cloud" }
          remoteDatabase.insertNodes(*operation.nodes.toTypedArray())
        }
        is UploadOperation.DeletePosition -> {
          LOGGER.i { "Deleting position ${operation.position} from cloud" }
          remoteDatabase.deletePosition(operation.position)
        }
        is UploadOperation.DeleteMove -> {
          LOGGER.i { "Deleting move ${operation.move} from ${operation.origin} in cloud" }
          remoteDatabase.deleteMove(operation.origin, operation.move)
        }
        is UploadOperation.DeleteAll -> {
          LOGGER.i { "Deleting all from cloud with hardFrom=${operation.hardFrom}" }
          remoteDatabase.deleteAll(operation.hardFrom)
        }
      }
    } catch (e: Exception) {
      LOGGER.e(e) { "Failed to process upload operation: $operation" }
      // Re-queue the operation for retry
      operationQueue.trySend(operation)
      delay(5000) // Wait before retrying
    }
  }

  /**
   * Queues nodes for upload to the cloud.
   *
   * @param nodes Nodes to upload
   */
  fun queueInsertNodes(vararg nodes: DataNode) {
    if (authManager.user == null) {
      LOGGER.d { "User not authenticated, skipping cloud upload queue" }
      return
    }
    operationQueue.trySend(UploadOperation.InsertNodes(nodes.toList()))
  }

  /**
   * Queues a position deletion for upload to the cloud.
   *
   * @param position Position to delete
   */
  fun queueDeletePosition(position: PositionIdentifier) {
    if (authManager.user == null) {
      LOGGER.d { "User not authenticated, skipping cloud upload queue" }
      return
    }
    operationQueue.trySend(UploadOperation.DeletePosition(position))
  }

  /**
   * Queues a move deletion for upload to the cloud.
   *
   * @param origin Origin position
   * @param move Move to delete
   */
  fun queueDeleteMove(origin: PositionIdentifier, move: String) {
    if (authManager.user == null) {
      LOGGER.d { "User not authenticated, skipping cloud upload queue" }
      return
    }
    operationQueue.trySend(UploadOperation.DeleteMove(origin, move))
  }

  /**
   * Queues a delete all operation for upload to the cloud.
   *
   * @param hardFrom Hard delete threshold
   */
  fun queueDeleteAll(hardFrom: Instant?) {
    if (authManager.user == null) {
      LOGGER.d { "User not authenticated, skipping cloud upload queue" }
      return
    }
    operationQueue.trySend(UploadOperation.DeleteAll(hardFrom))
  }

  /**
   * Uploads all data updated after the last sync to the cloud. This is called during the sign-in
   * synchronization process.
   */
  suspend fun uploadNewDataToCloud() {
    if (authManager.user == null) {
      LOGGER.w { "Cannot upload: user not authenticated" }
      return
    }

    val lastSync = LAST_SYNC.getValue()
    LOGGER.i { "Uploading data updated after $lastSync to cloud" }

    val allNodes = localDatabase.getAllNodes()
    val nodesToUpload = allNodes.filter { it.updatedAt > lastSync }

    if (nodesToUpload.isNotEmpty()) {
      LOGGER.i { "Uploading ${nodesToUpload.size} new/updated nodes to cloud" }
      remoteDatabase.insertNodes(*nodesToUpload.toTypedArray())
    } else {
      LOGGER.i { "No new data to upload" }
    }
  }

  /** Checks if the upload queue is empty. Useful for tests to wait for all uploads to complete. */
  fun isQueueEmpty(): Boolean {
    return operationQueue.isEmpty
  }
}

private val LOGGER = Logger.withTag("CloudUploader")
