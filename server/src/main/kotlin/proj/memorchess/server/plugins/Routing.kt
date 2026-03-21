package proj.memorchess.server.plugins

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.resources.Resources
import io.ktor.server.routing.routing
import proj.memorchess.server.routes.bookRoutes
import proj.memorchess.server.routes.healthRoutes

/** Installs type-safe routing and registers all route handlers. */
fun Application.configureRouting() {
  install(Resources)
  routing {
    healthRoutes()
    bookRoutes()
  }
}
