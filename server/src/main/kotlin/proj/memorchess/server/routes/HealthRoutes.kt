package proj.memorchess.server.routes

import io.ktor.server.resources.get
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import proj.memorchess.shared.routes.HealthRoute

/** Registers the health check endpoint. */
fun Route.healthRoutes() {
  get<HealthRoute> { call.respond(mapOf("status" to "ok")) }
}
