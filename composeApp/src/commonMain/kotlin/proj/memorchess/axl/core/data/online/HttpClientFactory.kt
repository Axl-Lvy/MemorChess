package proj.memorchess.axl.core.data.online

import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.resources.Resources
import io.ktor.client.request.header
import io.ktor.serialization.kotlinx.json.json
import proj.memorchess.shared.USER_ID_HEADER

/**
 * Creates an [HttpClient] configured for the MemorChess API.
 *
 * Installs type-safe resource routing, JSON content negotiation, and a default `X-User-Id` header.
 *
 * @param serverUrl The base URL of the MemorChess server (e.g. `http://localhost:8080`).
 * @param userIdProvider Provides the user's UUID for the request header.
 */
fun createBookApiClient(serverUrl: String, userIdProvider: UserIdProvider): HttpClient =
  HttpClient {
    install(Resources)
    install(ContentNegotiation) { json() }
    defaultRequest {
      url(serverUrl)
      header(USER_ID_HEADER, userIdProvider.getUserId())
    }
  }
