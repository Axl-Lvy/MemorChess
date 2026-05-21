@file:OptIn(ExperimentalWasmJsInterop::class)

package proj.memorchess.axl.core.data

import com.juul.indexeddb.Database
import com.juul.indexeddb.KeyPath
import com.juul.indexeddb.openDatabase
import kotlin.js.ExperimentalWasmJsInterop
import kotlinx.coroutines.CompletableDeferred

/**
 * Process wide IndexedDB singleton.
 *
 * Centralises the version upgrade logic for every object store the app uses, including the opening
 * tree (`nodes` and `moves`) and the Lichess explorer cache (`explorerCache`). Callers obtain the
 * database with [getIndexedDb] and then operate on the relevant object store directly.
 */
internal object IndexedDbInstance {

  private val deferred = CompletableDeferred<Database>()
  private var initialized = false

  suspend fun get(): Database {
    if (!initialized) {
      initialized = true
      val database =
        openDatabase(DB_NAME, DB_VERSION) { database, oldVersion, _ ->
          // Version 2: FSRS migration. The nodes store schema changed; drop and recreate.
          if (oldVersion in 1 until 2) {
            database.deleteObjectStore(NODES_STORE)
          }
          if (oldVersion < 2) {
            val nodesStore = database.createObjectStore(NODES_STORE, KeyPath("positionKey"))
            nodesStore.createIndex("isDeleted", KeyPath("isDeleted"), unique = false)
            nodesStore.createIndex("updatedAt", KeyPath("updatedAt"), unique = false)
            nodesStore.createIndex("dueDate", KeyPath("dueDate"), unique = false)
          }
          if (oldVersion < 1) {
            val movesStore =
              database.createObjectStore(MOVES_STORE, KeyPath("origin", "destination"))
            movesStore.createIndex("origin", KeyPath("origin"), unique = false)
            movesStore.createIndex("destination", KeyPath("destination"), unique = false)
            movesStore.createIndex("isDeleted", KeyPath("isDeleted"), unique = false)
            movesStore.createIndex("updatedAt", KeyPath("updatedAt"), unique = false)
          }
          // Version 3: explorer cache added.
          if (oldVersion < 3) {
            database.createObjectStore(EXPLORER_CACHE_STORE, KeyPath("key"))
          }
        }
      deferred.complete(database)
    }
    return deferred.await()
  }
}

internal suspend fun getIndexedDb(): Database = IndexedDbInstance.get()
