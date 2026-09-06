package proj.memorchess.axl.server.routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kotlin.time.Instant
import proj.memorchess.axl.core.sync.SyncPushRequest
import proj.memorchess.axl.server.TooLargeException
import proj.memorchess.axl.server.auth.SYNC_AUTH
import proj.memorchess.axl.server.auth.callerId
import proj.memorchess.axl.server.sync.SyncStore

/**
 * Largest page the server will serve. A larger `limit` is clamped to this rather than refused: page
 * size is a hint, and refusing one only makes a client guess.
 */
internal const val MAX_PULL_LIMIT: Int = 500

/** Page size used when the caller states none. */
private const val DEFAULT_PULL_LIMIT: Int = MAX_PULL_LIMIT

/**
 * Largest batch accepted in one push, counted across all three resources. This bounds how long one
 * transaction can hold row locks, which a byte cap alone does not.
 */
internal const val MAX_PUSH_ROWS: Int = 2_000

/**
 * Mounts the authenticated sync surface: `/v1/sync` in both directions and account deletion.
 *
 * @param clock Source of server time, so the clock skew boundary is testable.
 */
internal fun Route.syncRoutes(store: SyncStore, clock: () -> Instant) {
  authenticate(SYNC_AUTH) {
    get("/v1/sync") { call.respond(store.pull(call.callerId, since(), limit(), clock())) }

    post("/v1/sync") {
      val request = call.receive<SyncPushRequest>()
      val rows =
        request.nodes.size +
          request.edges.size +
          request.settings.size +
          request.repertoires.size +
          request.tags.size
      if (rows > MAX_PUSH_ROWS) {
        throw TooLargeException("a batch may carry at most $MAX_PUSH_ROWS rows, this one had $rows")
      }
      call.respond(store.push(call.callerId, request, clock()))
    }

    delete("/v1/me") {
      store.deleteUser(call.callerId)
      call.respond(HttpStatusCode.NoContent)
    }
  }
}

/**
 * The caller's cursor.
 *
 * A malformed cursor is refused rather than defaulted, because silently reading from `0` or from
 * some other revision hands back a plausible page that skips or repeats rows.
 */
private fun RoutingContext.since(): Long {
  val raw = call.request.queryParameters["since"] ?: return 0L
  val since = raw.toLongOrNull()
  if (since == null || since < 0) {
    throw BadRequestException("since must be a non negative integer, was '$raw'")
  }
  return since
}

/** The caller's requested page size, clamped to [MAX_PULL_LIMIT]. */
private fun RoutingContext.limit(): Int {
  val raw = call.request.queryParameters["limit"] ?: return DEFAULT_PULL_LIMIT
  val limit = raw.toIntOrNull()
  if (limit == null || limit <= 0) {
    throw BadRequestException("limit must be a positive integer, was '$raw'")
  }
  return minOf(limit, MAX_PULL_LIMIT)
}
