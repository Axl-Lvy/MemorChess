package proj.memorchess.axl.core.data.explorer

import proj.memorchess.axl.core.engine.Fen
import kotlinx.serialization.json.Json
import proj.memorchess.axl.core.data.CustomDatabase

/** Room backed [ExplorerCache]. */
internal class NonJsExplorerCache(database: CustomDatabase, private val json: Json) :
  ExplorerCache {

  private val dao = database.getExplorerCacheDao()

  override suspend fun get(fen: Fen, source: ExplorerSource): CachedExplorerEntry? {
    val row = dao.get(ExplorerCacheEntity.key(fen, source)) ?: return null
    val response = json.decodeFromString<LichessExplorerResponse>(row.json)
    return CachedExplorerEntry(response = response, fetchedAt = row.fetchedAt)
  }

  override suspend fun put(fen: Fen, source: ExplorerSource, response: LichessExplorerResponse) {
    val entry =
      ExplorerCacheEntity(
        key = ExplorerCacheEntity.key(fen, source),
        json = json.encodeToString(LichessExplorerResponse.serializer(), response),
        fetchedAt = proj.memorchess.axl.core.date.DateUtil.now(),
      )
    dao.upsert(entry)
  }

  override suspend fun eraseAll() {
    dao.eraseAll()
  }
}
