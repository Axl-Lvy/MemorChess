package proj.memorchess.axl.test_util

import kotlin.time.Instant
import proj.memorchess.axl.core.data.explorer.CachedExplorerEntry
import proj.memorchess.axl.core.data.explorer.ExplorerCache
import proj.memorchess.axl.core.data.explorer.ExplorerSource
import proj.memorchess.axl.core.data.explorer.LichessExplorerResponse
import proj.memorchess.axl.core.date.DateUtil

/** Test only in memory [ExplorerCache]. Not used by production code. */
class InMemoryExplorerCache : ExplorerCache {
  private val storage = mutableMapOf<String, CachedExplorerEntry>()

  fun seed(
    fen: String,
    source: ExplorerSource,
    response: LichessExplorerResponse,
    fetchedAt: Instant = DateUtil.now(),
  ) {
    storage[key(fen, source)] = CachedExplorerEntry(response, fetchedAt)
  }

  override suspend fun get(fen: String, source: ExplorerSource): CachedExplorerEntry? =
    storage[key(fen, source)]

  override suspend fun put(fen: String, source: ExplorerSource, response: LichessExplorerResponse) {
    storage[key(fen, source)] = CachedExplorerEntry(response, DateUtil.now())
  }

  override suspend fun eraseAll() {
    storage.clear()
  }

  private fun key(fen: String, source: ExplorerSource): String = "${source.name}:$fen"
}
