package proj.memorchess.axl.server

import io.github.tabilzad.ktor.annotations.GenerateOpenApi
import io.ktor.server.application.Application
import io.ktor.server.plugins.openapi.openAPI
import io.ktor.server.plugins.swagger.swaggerUI
import io.ktor.server.resources.get
import io.ktor.server.response.respondText
import io.ktor.server.routing.routing
import proj.memorchess.axl.server.routes.configureProtectedDataRoutes
import proj.memorchess.axl.server.routes.configureUserRelatedRoutes
import proj.memorchess.axl.shared.routes.Ping

private const val OPEN_API_FILE = "openapi/openapi.yaml"

@GenerateOpenApi
fun Application.configureRouting() {
  routing { openAPI(path = "openapi", swaggerFile = OPEN_API_FILE) }
  routing { swaggerUI(path = "swagger", swaggerFile = OPEN_API_FILE) }
  routing {
    get<Ping> { call.respondText("pong") }
    configureProtectedDataRoutes()
    configureUserRelatedRoutes()
  }
}
