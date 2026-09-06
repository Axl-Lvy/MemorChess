package proj.memorchess.axl.core.data.repertoire

import co.touchlab.kermit.Logger
import io.ktor.client.HttpClient
import io.ktor.client.plugins.onDownload
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import proj.memorchess.axl.core.pgn.PgnGame
import proj.memorchess.axl.core.pgn.PgnParseException
import proj.memorchess.axl.core.pgn.PgnParser

/**
 * HTTP client for the remote repertoire catalog.
 *
 * Originally served as static files from the `repertoire-data` branch on raw GitHub. Now served by
 * `:server` at the same relative paths (`manifest.json`, `pgn/<hash>.pgn`) under
 * [DEFAULT_BASE_URL]. The migration this client went through was a base URL change and nothing
 * else, because the two backends serve the identical `RepertoireManifest`/`RepertoireDescriptor`
 * contract.
 *
 * Responses are read as plain text, which both backends serve every file as, then decoded here: the
 * manifest with kotlinx.serialization (unknown fields tolerated) and PGN files with [PgnParser].
 * Every failure is mapped to a typed [CatalogResult] so callers never have to inspect Ktor
 * exceptions.
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
   * A manifest whose `schemaVersion` differs from 1 is reported as
   * [CatalogResult.MalformedManifest]; see [RepertoireManifest] for the rationale.
   */
  suspend fun fetchManifest(): CatalogResult<RepertoireManifest> {
    val body =
      when (val download = download(MANIFEST_FILE)) {
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
   * Fetches the PGN file at [file] (a path relative to the catalog root, as listed in
   * [RepertoireDescriptor.file]) and validates it with [PgnParser].
   *
   * A document that does not parse, or that contains no playable move at all, is reported as
   * [CatalogResult.MalformedPgn].
   *
   * @param onProgress Reports the fraction of the response body downloaded so far, from the
   *   underlying Ktor client's byte counter. Never called at all when the server's response carries
   *   no `Content-Length` (there is then nothing to divide by), so a caller must not assume it
   *   fires even once.
   */
  suspend fun fetchPgn(
    file: String,
    onProgress: (Float) -> Unit = {},
  ): CatalogResult<List<PgnGame>> {
    val body =
      when (val download = download(file, onProgress)) {
        is Download.Body -> download.text
        is Download.Failure -> return download.result
      }
    val games =
      try {
        PgnParser.parse(body)
      } catch (e: PgnParseException) {
        LOGGER.w(e) { "Catalog PGN $file does not parse" }
        return CatalogResult.MalformedPgn(e.message ?: "Invalid PGN")
      }
    if (games.all { it.moves.isEmpty() }) {
      return CatalogResult.MalformedPgn("PGN file $file contains no moves")
    }
    return CatalogResult.Ok(games)
  }

  private suspend fun download(path: String, onProgress: (Float) -> Unit = {}): Download =
    try {
      val response: HttpResponse =
        httpClient.get("$baseUrl/$path") {
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
    // Placeholder: `chess-server` has no public hostname yet. The Cloudflare tunnel and the
    // deployment that would give it one are later steps in the cloud backend sequencing spec
    // than the one that added this client migration, so there is nothing real to point at today.
    // The ".invalid" suffix is RFC 2606 reserved and never resolves. A build that ships before the
    // real hostname lands does not fail loudly: every request failure, this one included, maps to
    // CatalogResult.NetworkError, which the library screen renders the same way it renders being
    // offline, with nothing to tell the two apart. Replace with the real tunnel hostname once it
    // exists. The relative paths below already match what `:server` serves at that root
    // (`manifest.json`, `pgn/<sha256>.pgn`), so nothing else here changes.
    const val DEFAULT_BASE_URL = "https://chess.invalid/v1/repertoires"
    const val MANIFEST_FILE = "manifest.json"
    const val SUPPORTED_SCHEMA_VERSION = 1
  }
}

private val LOGGER = Logger.withTag("RepertoireCatalogClient")
