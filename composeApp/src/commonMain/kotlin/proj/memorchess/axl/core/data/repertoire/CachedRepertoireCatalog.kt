package proj.memorchess.axl.core.data.repertoire

import co.touchlab.kermit.Logger
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import kotlinx.serialization.json.Json
import proj.memorchess.axl.core.config.REPERTOIRE_MANIFEST_CACHE_SETTING
import proj.memorchess.axl.core.config.REPERTOIRE_MANIFEST_FETCHED_AT_SETTING
import proj.memorchess.axl.core.date.DateUtil

/**
 * Repertoire catalog manifest backed by a settings cache younger than [ttl], falling back to the
 * stale cache when a refresh fails.
 */
class CachedRepertoireCatalog(
  private val client: RepertoireCatalogClient,
  private val ttl: Duration = DEFAULT_TTL,
) {

  private val json = Json { ignoreUnknownKeys = true }

  /** Returns the catalog manifest, using the persisted cache when possible. */
  suspend fun getManifest(): CachedManifestResult {
    val cached = readCache()
    if (cached != null && DateUtil.now() - cached.fetchedAt <= ttl) {
      return CachedManifestResult.Fresh(cached.manifest)
    }
    return fetchAndCache(cached, bypassCache = false)
  }

  /**
   * Fetches the manifest now, bypassing every cache, falling back to the cached manifest as
   * [CachedManifestResult.Stale] when the fetch fails.
   */
  suspend fun refresh(): CachedManifestResult = fetchAndCache(readCache(), bypassCache = true)

  private suspend fun fetchAndCache(
    cached: CachedManifestEntry?,
    bypassCache: Boolean,
  ): CachedManifestResult {
    return when (val result = client.fetchManifest(bypassCache)) {
      is CatalogResult.Ok -> {
        writeCache(result.value)
        CachedManifestResult.Fresh(result.value)
      }
      is CatalogResult.NetworkError ->
        cached?.let { CachedManifestResult.Stale(it.manifest) }
          ?: CachedManifestResult.NetworkError(result.message)
      is CatalogResult.HttpError ->
        cached?.let { CachedManifestResult.Stale(it.manifest) }
          ?: CachedManifestResult.HttpError(result.status)
      // MalformedRepertoire never occurs for manifest fetches.
      is CatalogResult.MalformedManifest,
      is CatalogResult.MalformedRepertoire -> {
        val message =
          if (result is CatalogResult.MalformedManifest) result.message
          else (result as CatalogResult.MalformedRepertoire).message
        cached?.let { CachedManifestResult.Stale(it.manifest) }
          ?: CachedManifestResult.MalformedManifest(message)
      }
    }
  }

  private fun readCache(): CachedManifestEntry? {
    val raw = REPERTOIRE_MANIFEST_CACHE_SETTING.getValue()
    if (raw.isBlank()) {
      return null
    }
    val manifest =
      try {
        json.decodeFromString<RepertoireManifest>(raw)
      } catch (e: IllegalArgumentException) {
        LOGGER.w(e) { "Discarding cached manifest that no longer decodes" }
        return null
      }
    return CachedManifestEntry(manifest, REPERTOIRE_MANIFEST_FETCHED_AT_SETTING.getValue())
  }

  private fun writeCache(manifest: RepertoireManifest) {
    REPERTOIRE_MANIFEST_CACHE_SETTING.setValue(json.encodeToString(manifest))
    REPERTOIRE_MANIFEST_FETCHED_AT_SETTING.setValue(DateUtil.now())
  }

  private data class CachedManifestEntry(val manifest: RepertoireManifest, val fetchedAt: Instant)

  companion object {
    /** Default time before a cached manifest is considered stale. */
    val DEFAULT_TTL: Duration = 1.days
  }
}

/** Outcome of a [CachedRepertoireCatalog.getManifest] call. */
sealed class CachedManifestResult {

  /** Manifest is fresh, either from cache (within TTL) or just fetched. */
  data class Fresh(val manifest: RepertoireManifest) : CachedManifestResult()

  /** Network refresh failed but a previously cached manifest is available. */
  data class Stale(val manifest: RepertoireManifest) : CachedManifestResult()

  /** The request failed before an HTTP status was obtained and no cached manifest exists. */
  data class NetworkError(val message: String) : CachedManifestResult()

  /** The server answered with a non success HTTP [status] and no cached manifest exists. */
  data class HttpError(val status: Int) : CachedManifestResult()

  /** The downloaded manifest is invalid and no cached manifest exists. */
  data class MalformedManifest(val message: String) : CachedManifestResult()
}

private val LOGGER = Logger.withTag("CachedRepertoireCatalog")
