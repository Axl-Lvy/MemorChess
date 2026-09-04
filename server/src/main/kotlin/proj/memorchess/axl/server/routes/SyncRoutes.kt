package proj.memorchess.axl.server.routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kotlin.time.Instant
import proj.memorchess.axl.core.sync.SyncPushRequest
import proj.memorchess.axl.server.auth.SYNC_AUTH
import proj.memorchess.axl.server.auth.callerId
import proj.memorchess.axl.server.sync.SyncStore

/**
 * Mounts the authenticated sync surface.
 *
 * @param clock Source of server time, so the skew boundary is testable.
 */
internal fun Route.syncRoutes(store: SyncStore, clock: () -> Instant) {
  authenticate(SYNC_AUTH) {
    get("/v1/sync") { call.respond(store.pull(call.callerId, 0, 1, clock())) }
    post("/v1/sync") {
      call.respond(store.push(call.callerId, call.receive<SyncPushRequest>(), clock()))
    }
    delete("/v1/me") {
      store.deleteUser(call.callerId)
      call.respond(HttpStatusCode.NoContent)
    }
  }
}
