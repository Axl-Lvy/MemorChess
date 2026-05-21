package proj.memorchess.axl.core.data.explorer

import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import proj.memorchess.axl.core.date.DateUtil

/**
 * Combines a network [LichessExplorerClient] with a persistent [ExplorerCache].
 *
 * On [fetch] the cache is consulted first. A fresh entry within the source's TTL is returned
 * immediately. A stale entry is refreshed from the network; if the refresh fails for any reason,
 * the stale entry is returned with the staleness flagged so the UI can show a warning.
 *
 * Masters cache: indefinite (the OTB corpus is immutable history). Lichess cache: [lichessTtl],
 * default 7 days.
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
