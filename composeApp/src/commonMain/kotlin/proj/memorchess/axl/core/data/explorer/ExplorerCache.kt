package proj.memorchess.axl.core.data.explorer

import kotlin.time.Instant
import proj.memorchess.axl.core.engine.Fen

/**
 * Persistent cache for [LichessExplorerResponse] keyed by (fen, source).
 *
 * Implementations store the raw response as JSON plus a [fetchedAt] timestamp. TTL evaluation is
 * performed by callers; storage is content addressed and does not expire entries on its own.
 */
interface ExplorerCache {

  /** Looks up the cached entry for [fen] under [source], or `null` when missing. */
  suspend fun get(fen: Fen, source: ExplorerSource): CachedExplorerEntry?

  /** Writes [response] for [fen] under [source], replacing any previous entry. */
  suspend fun put(fen: Fen, source: ExplorerSource, response: LichessExplorerResponse)

  /** Removes every entry. */
  suspend fun eraseAll()
}

/** A cached entry returned by [ExplorerCache.get]. */
data class CachedExplorerEntry(val response: LichessExplorerResponse, val fetchedAt: Instant)

/** Provides the platform specific [ExplorerCache] backed by Room (nonJs) or IndexedDB (wasm). */
expect fun getPlatformSpecificExplorerCache(): ExplorerCache
