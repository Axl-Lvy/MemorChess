package proj.memorchess.axl.core.data.explorer

import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import proj.memorchess.axl.core.date.DateUtil

/**
 * Serves Lichess explorer responses from an [ExplorerCache], refreshing stale entries from the
 * network and falling back to the stale entry when the refresh fails.
 */
class CachedExplorer(
  private val client: LichessExplorerClient,
  private val cache: ExplorerCache,
  private val lichessTtl: Duration = DEFAULT_LICHESS_TTL,
) {

  /** Fetches the explorer response for [fen] from [source], using the cache when possible. */
  suspend fun fetch(source: ExplorerSource, fen: String): CachedExplorerResult {
    val cached = cache.get(fen, source)
    if (cached != null && isFresh(source, cached)) {
      return CachedExplorerResult.Fresh(cached.response)
    }
    return when (val networkResult = client.fetch(source, fen)) {
      is ExplorerResult.Ok -> {
        cache.put(fen, source, networkResult.response)
        CachedExplorerResult.Fresh(networkResult.response)
      }
      is ExplorerResult.RateLimited ->
        cached?.let { CachedExplorerResult.Stale(it.response) } ?: CachedExplorerResult.RateLimited
      is ExplorerResult.Unauthorized ->
        cached?.let { CachedExplorerResult.Stale(it.response) } ?: CachedExplorerResult.Unauthorized
      is ExplorerResult.NetworkError ->
        cached?.let { CachedExplorerResult.Stale(it.response) }
          ?: CachedExplorerResult.NetworkError(networkResult.message)
    }
  }

  private fun isFresh(source: ExplorerSource, entry: CachedExplorerEntry): Boolean =
    when (source) {
      ExplorerSource.MASTERS -> true
      ExplorerSource.LICHESS -> DateUtil.now() - entry.fetchedAt <= lichessTtl
    }

  companion object {
    /** Default time before a cached `lichess` response is considered stale. */
    val DEFAULT_LICHESS_TTL: Duration = 7.days
  }
}

/** Outcome of a [CachedExplorer.fetch] call. */
sealed class CachedExplorerResult {
  /** Response is fresh, either from cache (within TTL) or just fetched. */
  data class Fresh(val response: LichessExplorerResponse) : CachedExplorerResult()

  /** Network refresh failed but a previously cached entry is available. */
  data class Stale(val response: LichessExplorerResponse) : CachedExplorerResult()

  /** Lichess rate limited the client and no cached entry exists. */
  data object RateLimited : CachedExplorerResult()

  /** No token available or token rejected by Lichess, and no cached entry exists. */
  data object Unauthorized : CachedExplorerResult()

  /** Network failure and no cached entry exists. */
  data class NetworkError(val message: String) : CachedExplorerResult()
}
