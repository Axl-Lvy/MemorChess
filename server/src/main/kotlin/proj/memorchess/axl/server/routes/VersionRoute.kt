package proj.memorchess.axl.server.routes

import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.serialization.Serializable
import proj.memorchess.axl.server.BuildInfo

/** Body of `GET /v1/version`. */
@Serializable internal data class VersionResponse(val sha: String)

/**
 * Mounts `GET /v1/version`, unauthenticated: which commit is currently deployed, for rollback
 * decisions and deploy verification.
 */
internal fun Route.versionRoute() {
  get("/v1/version") { call.respond(VersionResponse(BuildInfo.sha)) }
}
