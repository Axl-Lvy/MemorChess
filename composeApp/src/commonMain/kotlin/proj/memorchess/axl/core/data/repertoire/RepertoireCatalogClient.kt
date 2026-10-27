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
 * HTTP client for the remote repertoire catalog, mapping every failure to a [CatalogResult].
 *
 * @param baseUrl Root URL of the catalog, without a trailing slash.
 */
class RepertoireCatalogClient(
  private val httpClient: HttpClient,
  private val baseUrl: String = DEFAULT_BASE_URL,
) {

  private val json = Json { ignoreUnknownKeys = true }

  /**
   * Fetches the catalog manifest; a `schemaVersion` other than 2 is
   * [CatalogResult.MalformedManifest].
   *
   * @param bypassCache Makes every HTTP cache, the browser's included, revalidate with the server.
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
   * Fetches the moves of version [version] of the repertoire [id]; an undecodable or empty list is
   * [CatalogResult.MalformedRepertoire].
   *
   * @param onProgress Downloaded fraction of the body; never called without a `Content-Length`.
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
   * Reports one completed install of the repertoire [id] to the catalog's popularity counter. Best
   * effort: failures are logged, never thrown.
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

  /** Outcome of the raw download, before content validation. */
  private sealed interface Download {
    data class Body(val text: String) : Download

    data class Failure(val result: CatalogResult<Nothing>) : Download
  }

  private companion object {
    /** Deliberately unresolvable, so a missing override fails as a network error. */
    const val DEFAULT_BASE_URL = "https://chess.invalid/v1/repertoires"
    const val MANIFEST_FILE = "manifest.json"
    const val SUPPORTED_SCHEMA_VERSION = 2
  }
}

private val LOGGER = Logger.withTag("RepertoireCatalogClient")
