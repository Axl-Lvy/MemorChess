package proj.memorchess.axl.server.routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.authenticate
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.NotFoundException
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import kotlin.time.Instant
import kotlin.uuid.Uuid
import proj.memorchess.axl.core.sync.SyncDeviceRegisterRequest
import proj.memorchess.axl.core.sync.SyncDeviceStatusResponse
import proj.memorchess.axl.core.sync.SyncPushRequest
import proj.memorchess.axl.server.RATE_LIMIT_SYNC_READ
import proj.memorchess.axl.server.RATE_LIMIT_SYNC_WRITE
import proj.memorchess.axl.server.TooLargeException
import proj.memorchess.axl.server.auth.SYNC_AUTH
import proj.memorchess.axl.server.auth.callerId
import proj.memorchess.axl.server.sync.RegisterOutcome
import proj.memorchess.axl.server.sync.ResyncRequiredException
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
    rateLimit(RATE_LIMIT_SYNC_READ) {
      get("/v1/me/devices/{deviceId}/status") {
        val synced =
          store.deviceStatus(call.callerId, call.deviceId())
            ?: throw NotFoundException("no such device")
        call.respond(SyncDeviceStatusResponse(synced))
      }

      get("/v1/sync") { call.respond(store.pull(call.callerId, device(), ack(), limit(), clock())) }
    }

    rateLimit(RATE_LIMIT_SYNC_WRITE) {
      post("/v1/sync") {
        val request = call.receive<SyncPushRequest>()
        val rows =
          request.nodes.size +
            request.edges.size +
            request.settings.size +
            request.repertoires.size +
            request.tags.size
        if (rows > MAX_PUSH_ROWS) {
          throw TooLargeException(
            "a batch may carry at most $MAX_PUSH_ROWS rows, this one had $rows"
          )
        }
        if (request.device.isEmpty()) {
          throw BadRequestException("device is required")
        }
        call.respond(store.push(call.callerId, request.device, request, clock()))
      }

      put("/v1/me/devices/{deviceId}") {
        val deviceId = call.deviceId()
        val request = call.receive<SyncDeviceRegisterRequest>()
        when (
          store.registerDevice(
            call.callerId,
            deviceId,
            request.platform,
            request.afterReset,
            clock(),
          )
        ) {
          RegisterOutcome.Ok -> call.respond(HttpStatusCode.NoContent)
          RegisterOutcome.ResyncRequired -> throw ResyncRequiredException(deviceId)
        }
      }

      delete("/v1/me") {
        store.deleteUser(call.callerId)
        call.respond(HttpStatusCode.NoContent)
      }
    }
  }
}

/**
 * The device this call is about, validated as a canonical UUID.
 *
 * The validation is the point of the constraint, not the storage: an invented value would create a
 * `sync_device` row that never pulls, holding that user's watermark down with no way to tell it
 * from a real install.
 */
private fun ApplicationCall.deviceId(): String {
  val raw = parameters["deviceId"].orEmpty()
  if (Uuid.parseOrNull(raw) == null) {
    throw BadRequestException("deviceId must be a canonical UUID, was '$raw'")
  }
  return raw
}

/**
 * The calling device.
 *
 * Required, because the server keeps that device's position and has nothing to serve from without
 * it.
 */
private fun RoutingContext.device(): String =
  call.request.queryParameters["device"] ?: throw BadRequestException("device is required")

/** Token of the page the caller has just written locally, absent when it has committed none. */
private fun RoutingContext.ack(): String? = call.request.queryParameters["ack"]

/** The caller's requested page size, clamped to [MAX_PULL_LIMIT]. */
private fun RoutingContext.limit(): Int {
  val raw = call.request.queryParameters["limit"] ?: return DEFAULT_PULL_LIMIT
  val limit = raw.toIntOrNull()
  if (limit == null || limit <= 0) {
    throw BadRequestException("limit must be a positive integer, was '$raw'")
  }
  return minOf(limit, MAX_PULL_LIMIT)
}
