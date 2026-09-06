package proj.memorchess.axl.server.routes

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.authenticate
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlin.time.Clock
import kotlin.time.Instant
import proj.memorchess.axl.core.data.repertoire.RepertoireManifest
import proj.memorchess.axl.core.sync.ApiError
import proj.memorchess.axl.core.sync.ApiErrorCode
import proj.memorchess.axl.server.auth.SYNC_AUTH
import proj.memorchess.axl.server.auth.callerId
import proj.memorchess.axl.server.repertoire.PublishOutcome
import proj.memorchess.axl.server.repertoire.PublishRepertoireRequest
import proj.memorchess.axl.server.repertoire.RemoveOutcome
import proj.memorchess.axl.server.repertoire.RepertoireCatalogPage
import proj.memorchess.axl.server.repertoire.RepertoireStatusRequest
import proj.memorchess.axl.server.repertoire.RepertoireStore
import proj.memorchess.axl.server.repertoire.SetStatusOutcome
import proj.memorchess.axl.server.repertoire.idProblem
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
 * Mounts the published repertoire catalog: the anonymous, cacheable reads that migrate the existing
 * `manifest.json`/PGN static file contract onto `:server` (spec 6.1), the paginated
 * `/v1/repertoires` surface from spec 5.7, authenticated publish and delete, and the admin
 * moderation kill switch.
 *
 * Additive to whatever routing an enclosing `syncModule` already installed: Ktor merges every
 * `routing {}` block on one `Application` into a single tree, so this never needs its own
 * `ContentNegotiation`/`StatusPages`/auth plugin installs.
 *
 * The admin moderation route carries no credential of its own: Cloudflare Access gates it at the
 * edge, following the pattern the home lab's other admin endpoints already use.
 */
internal fun Application.repertoireModule(
  store: RepertoireStore,
  clock: () -> Instant = Clock.System::now,
) {
  routing { repertoireRoutes(store, clock) }
}

private fun Route.repertoireRoutes(store: RepertoireStore, clock: () -> Instant) {
  get("/v1/repertoires/manifest.json") { getManifest(store) }
  get("/v1/repertoires/pgn/{sha256}.pgn") { getPayload(store) }
  get("/v1/repertoires") { getCatalogPage(store) }
  get("/v1/repertoires/{id}") { getById(store) }
  post("/v1/repertoires/{id}/installs") { recordInstall(store) }

  authenticate(SYNC_AUTH) {
    post("/v1/repertoires") { publish(store, clock) }
    delete("/v1/repertoires/{id}") { removeRepertoire(store) }
  }

  post("/admin/repertoires/{id}/status") { setStatus(store) }
}

private suspend fun RoutingContext.getManifest(store: RepertoireStore) {
  val rows = store.allPublished()
  val counts = store.countsFor(rows.map { it.id })
  val repertoires = rows.map { it.toDescriptor(counts.getValue(it.id)) }
  call.cacheControl(CATALOG_CACHE_CONTROL)
  call.respond(
    RepertoireManifest(schemaVersion = MANIFEST_SCHEMA_VERSION, repertoires = repertoires)
  )
}

private suspend fun RoutingContext.recordInstall(store: RepertoireStore) {
  val id = call.parameters["id"] ?: throw BadRequestException(MISSING_ID_MESSAGE)
  idProblem(id)?.let { throw BadRequestException(it) }
  store.recordInstall(id)
  call.respond(HttpStatusCode.NoContent)
}

private suspend fun RoutingContext.getPayload(store: RepertoireStore) {
  val sha256 = call.parameters["sha256"] ?: throw BadRequestException("missing payload hash")
  val bytes = store.readPayload(sha256)
  if (bytes == null) {
    call.respond(HttpStatusCode.NotFound, ApiError(ApiErrorCode.NOT_FOUND, "no such payload"))
  } else {
    call.cacheControl(BLOB_CACHE_CONTROL)
    call.respondBytes(bytes, ContentType.Text.Plain)
  }
}

private suspend fun RoutingContext.getCatalogPage(store: RepertoireStore) {
  val page = store.listPublished(cursor(), limit())
  val counts = store.countsFor(page.rows.map { it.id })
  call.cacheControl(CATALOG_CACHE_CONTROL)
  call.respond(
    RepertoireCatalogPage(
      nextCursor = page.nextCursor,
      repertoires = page.rows.map { it.toDescriptor(counts.getValue(it.id)) },
    )
  )
}

private suspend fun RoutingContext.getById(store: RepertoireStore) {
  val id = call.parameters["id"] ?: throw BadRequestException(MISSING_ID_MESSAGE)
  val row = store.get(id)
  if (row == null) {
    call.respondNoSuchRepertoire()
  } else {
    val count = store.countsFor(listOf(id)).getValue(id)
    call.cacheControl(CATALOG_CACHE_CONTROL)
    call.respond(row.toDescriptor(count))
  }
}

private suspend fun RoutingContext.publish(store: RepertoireStore, clock: () -> Instant) {
  val request = call.receive<PublishRepertoireRequest>()
  if (request.side != "white" && request.side != "black") {
    throw BadRequestException("side must be 'white' or 'black', was '${request.side}'")
  }
  val outcome =
    store.publish(
      authorId = call.callerId,
      id = request.id,
      title = request.title,
      description = request.description,
      side = request.side,
      pgn = request.pgn,
      now = clock(),
    )
  respondToPublishOutcome(outcome)
}

private suspend fun RoutingContext.respondToPublishOutcome(outcome: PublishOutcome) {
  when (outcome) {
    is PublishOutcome.Published -> call.respond(HttpStatusCode.Created, outcome.row.toDescriptor())
    is PublishOutcome.InvalidPayload ->
      call.respond(HttpStatusCode.BadRequest, ApiError(ApiErrorCode.INVALID_PGN, outcome.reason))
    is PublishOutcome.PayloadTooLarge ->
      call.respond(HttpStatusCode.PayloadTooLarge, ApiError(ApiErrorCode.TOO_LARGE, outcome.reason))
    PublishOutcome.Forbidden ->
      call.respond(
        HttpStatusCode.Forbidden,
        ApiError(ApiErrorCode.FORBIDDEN, "this id belongs to a different author"),
      )
    PublishOutcome.Removed ->
      call.respond(
        HttpStatusCode.Forbidden,
        ApiError(
          ApiErrorCode.FORBIDDEN,
          "this id was removed by a moderator and cannot be republished",
        ),
      )
    is PublishOutcome.QuotaExceeded ->
      call.respond(HttpStatusCode.Forbidden, ApiError(ApiErrorCode.QUOTA_EXCEEDED, outcome.reason))
    is PublishOutcome.Failed ->
      call.respond(
        HttpStatusCode.InternalServerError,
        ApiError(ApiErrorCode.INTERNAL, "the server failed to validate the payload"),
      )
  }
}

private suspend fun RoutingContext.removeRepertoire(store: RepertoireStore) {
  val id = call.parameters["id"] ?: throw BadRequestException(MISSING_ID_MESSAGE)
  when (store.remove(call.callerId, id)) {
    RemoveOutcome.Removed -> call.respond(HttpStatusCode.NoContent)
    RemoveOutcome.NotFound -> call.respondNoSuchRepertoire()
    RemoveOutcome.Forbidden ->
      call.respond(
        HttpStatusCode.Forbidden,
        ApiError(ApiErrorCode.FORBIDDEN, "only the author may remove this repertoire"),
      )
  }
}

private suspend fun RoutingContext.setStatus(store: RepertoireStore) {
  val id = call.parameters["id"] ?: throw BadRequestException(MISSING_ID_MESSAGE)
  val request = call.receive<RepertoireStatusRequest>()
  if (request.status !in VALID_STATUSES) {
    throw BadRequestException("status must be one of $VALID_STATUSES, was '${request.status}'")
  }
  when (val outcome = store.setStatus(id, request.status)) {
    is SetStatusOutcome.Updated -> call.respond(outcome.row.toDescriptor())
    SetStatusOutcome.NotFound -> call.respondNoSuchRepertoire()
  }
}

private suspend fun ApplicationCall.respondNoSuchRepertoire() {
  respond(HttpStatusCode.NotFound, ApiError(ApiErrorCode.NOT_FOUND, NO_SUCH_REPERTOIRE_MESSAGE))
}

/** The status values a repertoire row may hold. See spec 6.6. */
private val VALID_STATUSES = setOf("published", "unlisted", "removed")

private const val MISSING_ID_MESSAGE = "missing id"
private const val NO_SUCH_REPERTOIRE_MESSAGE = "no such repertoire"

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
