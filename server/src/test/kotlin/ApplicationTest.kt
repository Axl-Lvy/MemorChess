package proj.memorchess.axl.server

import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BasicAuthCredentials
import io.ktor.client.plugins.auth.providers.basic
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.resources.Resources
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication

/**
 * Creates a test application with test database configuration. Each test gets a fresh database
 * state with the same seeded data.
 *
 * @param block The test code to execute
 */
fun testApplicationWithTestModule(block: suspend ApplicationTestBuilder.() -> Unit) {
  testApplication {
    environment { config = ApplicationConfig("application-test.yaml") }
    client = createClient {
      install(ContentNegotiation) { json() }
      install(Resources)
    }
    startApplication()
    block()
  }
}

fun testAuthenticated(block: suspend ApplicationTestBuilder.() -> Unit) =
  testApplicationWithTestModule {
    client =
      client.config {
        install(Auth) {
          basic {
            credentials { BasicAuthCredentials(username = "admin", password = "admin") }
            sendWithoutRequest { request -> request.url.host.contains("localhost") }
          }
        }
      }
    block()
  }

fun Application.module() {
  configureSerialization()
  configureTestDatabase()
  configureSecurity()
  configureRouting()
}
