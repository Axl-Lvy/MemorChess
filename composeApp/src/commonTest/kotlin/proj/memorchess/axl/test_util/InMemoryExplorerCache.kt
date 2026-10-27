package proj.memorchess.axl.test_util

import kotlin.time.Instant
import proj.memorchess.axl.core.data.explorer.CachedExplorerEntry
import proj.memorchess.axl.core.data.explorer.ExplorerCache
import proj.memorchess.axl.core.data.explorer.ExplorerSource
import proj.memorchess.axl.core.data.explorer.LichessExplorerResponse
import proj.memorchess.axl.core.date.DateUtil
import proj.memorchess.axl.core.engine.Fen

/** In memory [ExplorerCache]. */
class InMemoryExplorerCache : ExplorerCache {
  private val storage = mutableMapOf<String, CachedExplorerEntry>()

  fun seed(
    fen: Fen,
    source: ExplorerSource,
    response: LichessExplorerResponse,
    fetchedAt: Instant = DateUtil.now(),
  ) {
    storage[key(fen, source)] = CachedExplorerEntry(response, fetchedAt)
  }

  override suspend fun get(fen: Fen, source: ExplorerSource): CachedExplorerEntry? =
    storage[key(fen, source)]

  override suspend fun put(fen: Fen, source: ExplorerSource, response: LichessExplorerResponse) {
    storage[key(fen, source)] = CachedExplorerEntry(response, DateUtil.now())
  }

  override suspend fun eraseAll() {
    storage.clear()
  }

  private fun key(fen: Fen, source: ExplorerSource): String = "${source.name}:${fen.value}"
}
