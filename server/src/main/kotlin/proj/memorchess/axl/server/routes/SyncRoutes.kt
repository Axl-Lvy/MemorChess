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
import proj.memorchess.axl.core.sync.DeviceId
import proj.memorchess.axl.core.sync.DevicePlatform
import proj.memorchess.axl.core.sync.SyncDeviceRegisterRequest
import proj.memorchess.axl.core.sync.SyncDeviceStatusResponse
import proj.memorchess.axl.core.sync.SyncEraseRequest
import proj.memorchess.axl.core.sync.SyncEraseResponse
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
 * Mounts the authenticated sync surface: `/v1/sync` in both directions, the erase, and account
 * deletion.
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
        val device = requireDevice(request.device.value, "device")
        call.respond(store.push(call.callerId, device, request, clock()))
      }

      put("/v1/me/devices/{deviceId}") {
        val deviceId = call.deviceId()
        val request = call.receive<SyncDeviceRegisterRequest>()
        // A plain string on the wire, so an unknown platform never fails decoding.
        val platform =
          DevicePlatform.fromWire(request.platform)
            ?: throw BadRequestException("unknown platform '${request.platform}'")
        when (
          store.registerDevice(
            call.callerId,
            deviceId,
            platform,
            request.afterReset,
            clock(),
          )
        ) {
          RegisterOutcome.Ok -> call.respond(HttpStatusCode.NoContent)
          RegisterOutcome.ResyncRequired -> throw ResyncRequiredException(deviceId)
        }
      }

      post("/v1/me/erase") { erase(store, clock()) }

      delete("/v1/me") {
        store.deleteUser(call.callerId)
        call.respond(HttpStatusCode.NoContent)
      }
    }
  }
}

/** Erases the caller's synced data as of [serverNow] and answers with the highest revision. */
private suspend fun RoutingContext.erase(store: SyncStore, serverNow: Instant) {
  val request = call.receive<SyncEraseRequest>()
  val device = requireDevice(request.device.value, "device")
  if (request.deviceSeq < 1) {
    throw BadRequestException("deviceSeq must be at least 1, was ${request.deviceSeq}")
  }
  val revision = store.erase(call.callerId, device, request.deviceSeq, serverNow)
  call.respond(SyncEraseResponse(serverNow, revision))
}

/**
 * The device this call is about, validated as a canonical UUID: an invented one would create a
 * device row that never pulls and holds the user's watermark down.
 */
private fun ApplicationCall.deviceId(): DeviceId =
  requireDevice(parameters["deviceId"].orEmpty(), "deviceId")

/** The device [raw] names, or a `400` naming [field] unless it passes [DeviceId.parseOrNull]. */
private fun requireDevice(raw: String, field: String): DeviceId =
  DeviceId.parseOrNull(raw)
    ?: throw BadRequestException(
      "$field must be a canonical UUID other than the nil one, was '$raw'"
    )

/** The calling device, required because the server serves from that device's position. */
private fun RoutingContext.device(): DeviceId =
  requireDevice(
    call.request.queryParameters["device"] ?: throw BadRequestException("device is required"),
    "device",
  )

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
