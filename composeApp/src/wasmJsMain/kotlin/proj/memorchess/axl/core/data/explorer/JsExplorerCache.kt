@file:OptIn(ExperimentalWasmJsInterop::class)
@file:Suppress("UNCHECKED_CAST_TO_EXTERNAL_INTERFACE")

package proj.memorchess.axl.core.data.explorer

import com.juul.indexeddb.Key
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsAny
import kotlin.js.toJsString
import kotlin.time.Instant
import kotlinx.serialization.json.Json
import proj.memorchess.axl.core.data.EXPLORER_CACHE_STORE
import proj.memorchess.axl.core.data.getIndexedDb
import proj.memorchess.axl.core.date.DateUtil

private external interface JsExplorerCacheEntity : JsAny {
  var key: String
  var json: String
  var fetchedAtEpochSeconds: Double
}

private fun <T : JsAny> emptyObject(): T = js("({})")

/** IndexedDB backed [ExplorerCache] for the wasmJs target. */
internal class JsExplorerCache(private val json: Json) : ExplorerCache {

  override suspend fun get(fen: String, source: ExplorerSource): CachedExplorerEntry? {
    val database = getIndexedDb()
    return database.transaction(EXPLORER_CACHE_STORE) {
      val row =
        objectStore(EXPLORER_CACHE_STORE)
          .get(Key(buildKey(fen, source).toJsString()))
          ?.unsafeCast<JsExplorerCacheEntity>() ?: return@transaction null
      val response = json.decodeFromString<LichessExplorerResponse>(row.json)
      CachedExplorerEntry(
        response = response,
        fetchedAt = Instant.fromEpochSeconds(row.fetchedAtEpochSeconds.toLong()),
      )
    }
  }

  override suspend fun put(fen: String, source: ExplorerSource, response: LichessExplorerResponse) {
    val database = getIndexedDb()
    val entry =
      emptyObject<JsExplorerCacheEntity>().apply {
        key = buildKey(fen, source)
        this.json =
          this@JsExplorerCache.json.encodeToString(LichessExplorerResponse.serializer(), response)
        fetchedAtEpochSeconds = DateUtil.now().epochSeconds.toDouble()
      }
    database.writeTransaction(EXPLORER_CACHE_STORE) { objectStore(EXPLORER_CACHE_STORE).put(entry) }
  }

  override suspend fun eraseAll() {
    val database = getIndexedDb()
    database.writeTransaction(EXPLORER_CACHE_STORE) { objectStore(EXPLORER_CACHE_STORE).clear() }
  }

  private fun buildKey(fen: String, source: ExplorerSource): String = "${source.path}:$fen"
}
