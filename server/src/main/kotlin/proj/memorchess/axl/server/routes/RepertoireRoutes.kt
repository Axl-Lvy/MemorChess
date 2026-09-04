package proj.memorchess.axl.server.routes

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import proj.memorchess.axl.core.data.repertoire.RepertoireManifest
import proj.memorchess.axl.core.sync.ApiError
import proj.memorchess.axl.core.sync.ApiErrorCode
import proj.memorchess.axl.server.repertoire.RepertoireCatalogPage
import proj.memorchess.axl.server.repertoire.RepertoireStore
import proj.memorchess.axl.server.repertoire.toDescriptor

/** Largest page [RepertoireStore.listPublished] will be asked to serve in one call. */
internal const val MAX_CATALOG_LIMIT: Int = 100

/** Page size used when the caller states none. */
private const val DEFAULT_CATALOG_LIMIT: Int = 50

/** `schemaVersion` published in `manifest.json`. See spec 6.1: bumped only on a breaking change. */
private const val MANIFEST_SCHEMA_VERSION: Int = 1

/**
 * Short TTL for the anonymous, unpersonalized catalog responses, so Cloudflare's edge can cache
 * them without ever serving one caller's data to another (spec 5.7).
 */
private const val CATALOG_CACHE_CONTROL = "public, max-age=60"

/** Far future, immutable: a PGN blob's content never changes once published (spec 6.2). */
private const val BLOB_CACHE_CONTROL = "public, max-age=31536000, immutable"

/**
 * Mounts the published repertoire catalog: the anonymous, cacheable reads that migrate the
 * existing `manifest.json`/PGN static file contract onto `:server` (spec 6.1), plus the paginated
 * `/v1/repertoires` surface from spec 5.7.
 */
internal fun Application.repertoireModule(store: RepertoireStore) {
  routing { repertoireRoutes(store) }
}

private fun Route.repertoireRoutes(store: RepertoireStore) {
  get("/v1/repertoires/manifest.json") {
    val repertoires = store.allPublished().map { it.toDescriptor() }
    call.cacheControl(CATALOG_CACHE_CONTROL)
    call.respond(RepertoireManifest(schemaVersion = MANIFEST_SCHEMA_VERSION, repertoires = repertoires))
  }

  get("/v1/repertoires/pgn/{sha256}.pgn") {
    val sha256 = call.parameters["sha256"] ?: throw BadRequestException("missing payload hash")
    val bytes = store.readPayload(sha256)
    if (bytes == null) {
      call.respond(HttpStatusCode.NotFound, ApiError(ApiErrorCode.NOT_FOUND, "no such payload"))
    } else {
      call.cacheControl(BLOB_CACHE_CONTROL)
      call.respondBytes(bytes, ContentType.Text.Plain)
    }
  }

  get("/v1/repertoires") {
    val page = store.listPublished(cursor(), limit())
    call.cacheControl(CATALOG_CACHE_CONTROL)
    call.respond(
      RepertoireCatalogPage(nextCursor = page.nextCursor, repertoires = page.rows.map { it.toDescriptor() })
    )
  }

  get("/v1/repertoires/{id}") {
    val id = call.parameters["id"] ?: throw BadRequestException("missing id")
    val row = store.get(id)
    if (row == null) {
      call.respond(HttpStatusCode.NotFound, ApiError(ApiErrorCode.NOT_FOUND, "no such repertoire"))
    } else {
      call.cacheControl(CATALOG_CACHE_CONTROL)
      call.respond(row.toDescriptor())
    }
  }
}

private fun ApplicationCall.cacheControl(value: String) {
  response.headers.append(HttpHeaders.CacheControl, value)
}

/** The caller's requested page cursor, the last id of the previous page. */
private fun RoutingContext.cursor(): String? = call.request.queryParameters["cursor"]

/** The caller's requested page size, clamped to [MAX_CATALOG_LIMIT]. */
private fun RoutingContext.limit(): Int {
  val raw = call.request.queryParameters["limit"] ?: return DEFAULT_CATALOG_LIMIT
  val limit = raw.toIntOrNull()
  if (limit == null || limit <= 0) {
    throw BadRequestException("limit must be a positive integer, was '$raw'")
  }
  return minOf(limit, MAX_CATALOG_LIMIT)
}
