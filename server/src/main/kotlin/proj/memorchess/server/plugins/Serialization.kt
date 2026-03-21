package proj.memorchess.server.plugins

import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation

/** Installs JSON content negotiation. */
fun Application.configureSerialization() {
  install(ContentNegotiation) { json() }
}
