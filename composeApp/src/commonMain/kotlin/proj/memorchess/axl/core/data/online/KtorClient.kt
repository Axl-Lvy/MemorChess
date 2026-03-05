package proj.memorchess.axl.core.data.online

import io.ktor.client.HttpClient
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BasicAuthCredentials
import io.ktor.client.plugins.auth.providers.basic
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.resources.Resources
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

private const val SERVER_URL = "http://localhost:8080"

/**
 * Mutable holder for basic auth credentials. Updated by [KtorAuthManager] on sign-in / sign-out.
 */
object CredentialsHolder {
  var username: String = ""
  var password: String = ""
}

/**
 * Creates and configures the Ktor HTTP client for the application.
 *
 * The client is configured with:
 * - JSON content negotiation for serialization
 * - Basic authentication using [CredentialsHolder]
 */
fun createKtorClient(): HttpClient {
  return HttpClient {
    defaultRequest { url(SERVER_URL) }
    install(Resources)
    install(ContentNegotiation) {
      json(
        Json {
          prettyPrint = true
          isLenient = true
          ignoreUnknownKeys = true
        }
      )
    }
    install(Auth) {
      basic {
        credentials {
          BasicAuthCredentials(
            username = CredentialsHolder.username,
            password = CredentialsHolder.password,
          )
        }
        sendWithoutRequest { true }
      }
    }
  }
}

/** The base URL for the server API */
const val BASE_URL = SERVER_URL
