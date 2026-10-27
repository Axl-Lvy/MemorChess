package proj.memorchess.axl.core.data.repertoire

import co.touchlab.kermit.Logger
import io.ktor.client.HttpClient
import io.ktor.client.plugins.onDownload
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json

/**
 * HTTP client for the remote repertoire catalog.
 *
 * Served by `:server` at [DEFAULT_BASE_URL] (`manifest.json`, `<id>/versions/<version>/moves`); the catalog is
 * populated by users publishing their own repertoires (see `RepertoirePublishClient`), not by any
 * static file source.
 *
 * Responses are read as plain text, then decoded here: the manifest with kotlinx.serialization
 * (unknown fields tolerated) and move lists, both with kotlinx.serialization. Every failure is mapped to a typed
 * [CatalogResult] so callers never have to inspect Ktor exceptions — except [reportInstall], a fire
 * and forget write with no result to map, since a caller's own install outcome must never depend on
 * this telemetry succeeding.
 *
 * @param httpClient The Ktor client used for requests.
 * @param baseUrl Root URL of the catalog, without a trailing slash. Injectable for tests.
 */
class RepertoireCatalogClient(
  private val httpClient: HttpClient,
  private val baseUrl: String = DEFAULT_BASE_URL,
) {

  private val json = Json { ignoreUnknownKeys = true }

  /**
   * Fetches and validates `manifest.json` from the catalog root.
   *
   * A manifest whose `schemaVersion` differs from 2 is reported as
   * [CatalogResult.MalformedManifest]; see [RepertoireManifest] for the rationale.
   *
   * @param bypassCache Asks every HTTP cache on the way (the browser's included) to revalidate with
   *   the server instead of answering from a cached copy.
   */
  suspend fun fetchManifest(bypassCache: Boolean = false): CatalogResult<RepertoireManifest> {
    val body =
      when (val download = download(MANIFEST_FILE, bypassCache = bypassCache)) {
        is Download.Body -> download.text
        is Download.Failure -> return download.result
      }
    val manifest =
      try {
        json.decodeFromString<RepertoireManifest>(body)
      } catch (e: IllegalArgumentException) {
        LOGGER.w(e) { "Catalog manifest does not decode" }
        return CatalogResult.MalformedManifest(e.message ?: "Invalid manifest JSON")
      }
    if (manifest.schemaVersion != SUPPORTED_SCHEMA_VERSION) {
      return CatalogResult.MalformedManifest(
        "Unsupported manifest schema version ${manifest.schemaVersion}," +
          " expected $SUPPORTED_SCHEMA_VERSION"
      )
    }
    return CatalogResult.Ok(manifest)
  }

  /**
   * Fetches the moves of version [version] of the repertoire [id].
   *
   * A body that does not decode, or that holds no move at all, is reported as
   * [CatalogResult.MalformedRepertoire].
   *
   * @param onProgress Reports the fraction of the response body downloaded so far, from the
   *   underlying Ktor client's byte counter. Never called at all when the server's response carries
   *   no `Content-Length` (there is then nothing to divide by), so a caller must not assume it
   *   fires even once.
   */
  suspend fun fetchMoves(
    id: String,
    version: Int,
    onProgress: (Float) -> Unit = {},
  ): CatalogResult<List<RepertoireMove>> {
    val path = "$id/versions/$version/moves"
    val body =
      when (val download = download(path, onProgress)) {
        is Download.Body -> download.text
        is Download.Failure -> return download.result
      }
    val moves =
      try {
        json.decodeFromString<RepertoireMoveList>(body).moves
      } catch (e: IllegalArgumentException) {
        LOGGER.w(e) { "Catalog moves $path do not decode" }
        return CatalogResult.MalformedRepertoire(e.message ?: "Invalid move list")
      }
    if (moves.isEmpty()) {
      return CatalogResult.MalformedRepertoire("Repertoire $path contains no moves")
    }
    return CatalogResult.Ok(moves)
  }

  /**
   * Reports one completed install of the repertoire [id], for the anonymous popularity counter the
   * catalog's `downloadCount` field carries (see [RepertoireDescriptor.downloadCount]'s KDoc). Best
   * effort: a network failure or a non success HTTP status is logged and otherwise swallowed, never
   * thrown, so a caller's own install outcome is never disturbed by this being unreachable.
   */
  suspend fun reportInstall(id: String) {
    try {
      val response = httpClient.post("$baseUrl/$id/installs")
      if (!response.status.isSuccess()) {
        LOGGER.w { "Reporting install of repertoire $id got ${response.status}" }
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      LOGGER.w(e) { "Reporting install of repertoire $id failed" }
    }
  }

  private suspend fun download(
    path: String,
    onProgress: (Float) -> Unit = {},
    bypassCache: Boolean = false,
  ): Download =
    try {
      val response: HttpResponse =
        httpClient.get("$baseUrl/$path") {
          if (bypassCache) {
            header(HttpHeaders.CacheControl, "no-cache")
          }
          onDownload { bytesSentTotal, contentLength ->
            if (contentLength != null && contentLength > 0) {
              onProgress((bytesSentTotal.toFloat() / contentLength.toFloat()).coerceIn(0f, 1f))
            }
          }
        }
      if (response.status.isSuccess()) {
        Download.Body(response.bodyAsText())
      } else {
        LOGGER.w { "Catalog returned ${response.status} for $path" }
        Download.Failure(CatalogResult.HttpError(response.status.value))
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      LOGGER.w(e) { "Catalog request failed for $path" }
      Download.Failure(CatalogResult.NetworkError(e.message ?: "Request failed"))
    }

  /** Outcome of the raw text download step, before any content validation. */
  private sealed interface Download {
    /** The body of a successful response. */
    data class Body(val text: String) : Download

    /** The failure to surface to the caller unchanged. */
    data class Failure(val result: CatalogResult<Nothing>) : Download
  }

  private companion object {
    /** Deliberately unresolvable; see the class doc. */
    const val DEFAULT_BASE_URL = "https://chess.invalid/v1/repertoires"
    const val MANIFEST_FILE = "manifest.json"
    const val SUPPORTED_SCHEMA_VERSION = 2
  }
}

private val LOGGER = Logger.withTag("RepertoireCatalogClient")
