package proj.memorchess.axl.core.data.online

import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

private const val SERVER_URL = "http://localhost:8080"

/**
 * Creates and configures the Ktor HTTP client for the application.
 *
 * The client is configured with:
 * - JSON content negotiation for serialization
 */
fun createKtorClient(): HttpClient {
  return HttpClient {
    install(ContentNegotiation) {
      json(
        Json {
          prettyPrint = true
          isLenient = true
          ignoreUnknownKeys = true
        }
      )
    }
  }
}

/** The base URL for the server API */
const val BASE_URL = SERVER_URL
