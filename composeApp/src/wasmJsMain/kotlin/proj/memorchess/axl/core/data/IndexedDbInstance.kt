@file:OptIn(ExperimentalWasmJsInterop::class)

package proj.memorchess.axl.core.data

import com.juul.indexeddb.Database
import com.juul.indexeddb.KeyPath
import com.juul.indexeddb.VersionChangeTransaction
import com.juul.indexeddb.openDatabase
import kotlin.js.ExperimentalWasmJsInterop
import kotlinx.coroutines.CompletableDeferred

/**
 * Process wide IndexedDB singleton.
 *
 * Owns the object stores the app uses: the opening tree (`nodes` and `moves`) and the Lichess
 * explorer cache (`explorerCache`). Callers obtain the database with [getIndexedDb] and then
 * operate on the relevant object store directly.
 *
 * The app is not in production, so there are no migrations: any schema change is applied by bumping
 * [DB_VERSION], which drops every store and rebuilds it from scratch — see [recreate].
 */
internal object IndexedDbInstance {

  private val deferred = CompletableDeferred<Database>()
  private var initialized = false

  suspend fun get(): Database {
    if (!initialized) {
      initialized = true
      val database = openDatabase(DB_NAME, DB_VERSION) { db, _, _ -> recreate(db) }
      deferred.complete(database)
    }
    return deferred.await()
  }

  /**
   * Drops every object store and rebuilds it from the current schema. Existing rows are discarded;
   * this is the destructive-recreate counterpart to Room's `fallbackToDestructiveMigration` and the
   * reason no per-version migration code is needed. The deletes are guarded because a store does
   * not exist yet on a fresh install (the upgrade runs with no prior version).
   */
  private fun VersionChangeTransaction.recreate(database: Database) {
    listOf(NODES_STORE, MOVES_STORE, EXPLORER_CACHE_STORE).forEach { store ->
      runCatching { database.deleteObjectStore(store) }
    }

    val nodesStore = database.createObjectStore(NODES_STORE, KeyPath("positionKey"))
    nodesStore.createIndex("isDeleted", KeyPath("isDeleted"), unique = false)
    nodesStore.createIndex("updatedAt", KeyPath("updatedAt"), unique = false)
    nodesStore.createIndex("dueDate", KeyPath("dueDate"), unique = false)
    nodesStore.createIndex("firstReview", KeyPath("firstReview"), unique = false)
    nodesStore.createIndex("lastReview", KeyPath("lastReview"), unique = false)
    nodesStore.createIndex(
      "good_phase_due",
      KeyPath("hasGoodOutgoing", "phase", "dueDate"),
      unique = false,
    )
    nodesStore.createIndex(
      "good_phase_due_depth_created",
      KeyPath("hasGoodOutgoing", "phase", "dueDate", "depth", "createdAt"),
      unique = false,
    )

    val movesStore = database.createObjectStore(MOVES_STORE, KeyPath("origin", "destination"))
    movesStore.createIndex("origin", KeyPath("origin"), unique = false)
    movesStore.createIndex("destination", KeyPath("destination"), unique = false)
    movesStore.createIndex("isDeleted", KeyPath("isDeleted"), unique = false)
    movesStore.createIndex("updatedAt", KeyPath("updatedAt"), unique = false)

    database.createObjectStore(EXPLORER_CACHE_STORE, KeyPath("key"))
  }
}

internal suspend fun getIndexedDb(): Database = IndexedDbInstance.get()
