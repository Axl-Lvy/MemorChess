package proj.memorchess.axl.server

import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.resources.Resources

fun Application.configureSerialization() {
  install(ContentNegotiation) { json() }
  install(Resources)
}
