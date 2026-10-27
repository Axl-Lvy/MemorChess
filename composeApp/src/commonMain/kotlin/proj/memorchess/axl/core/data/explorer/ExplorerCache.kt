package proj.memorchess.axl.core.data.explorer

import kotlin.time.Instant

/**
 * Persistent cache for [LichessExplorerResponse] keyed by (fen, source). Entries never expire;
 * callers apply the TTL to [CachedExplorerEntry.fetchedAt].
 */
interface ExplorerCache {

  /** Looks up the cached entry for [fen] under [source], or `null` when missing. */
  suspend fun get(fen: String, source: ExplorerSource): CachedExplorerEntry?

  /** Writes [response] for [fen] under [source], replacing any previous entry. */
  suspend fun put(fen: String, source: ExplorerSource, response: LichessExplorerResponse)

  /** Removes every entry. */
  suspend fun eraseAll()
}

/** A cached entry returned by [ExplorerCache.get]. */
data class CachedExplorerEntry(val response: LichessExplorerResponse, val fetchedAt: Instant)

/** Provides the platform specific [ExplorerCache] backed by Room (nonJs) or IndexedDB (wasm). */
expect fun getPlatformSpecificExplorerCache(): ExplorerCache
