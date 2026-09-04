package proj.memorchess.axl.core.sync

import co.touchlab.kermit.Logger
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess

/**
 * HTTP client for `:server`'s `/v1/sync`. Every failure is mapped to a typed outcome so
 * [SyncEngine] never has to inspect a Ktor exception, mirroring
 * [proj.memorchess.axl.core.data.repertoire.RepertoireCatalogClient].
 *
 * @param baseUrl Root URL, without a trailing slash. [DEFAULT_BASE_URL] is a deliberately
 *   unresolvable placeholder, same convention as `RepertoireCatalogClient.DEFAULT_BASE_URL`: no
 *   public server domain exists yet, so every request degrades to [SyncPullOutcome.Error]/
 *   [SyncPushOutcome.Error] rather than pretending to succeed.
 */
class SyncApiClient(
  private val httpClient: HttpClient,
  private val baseUrl: String = DEFAULT_BASE_URL,
) {

  /** Pulls rows changed since [since] (`null` for the first page), capped at [limit]. */
  suspend fun pull(accessToken: String, since: Long?, limit: Int): SyncPullOutcome {
    return try {
      val response: HttpResponse =
        httpClient.get("$baseUrl/sync") {
          bearerAuth(accessToken)
          if (since != null) parameter("since", since)
          parameter("limit", limit)
        }
      when {
        response.status.isSuccess() -> SyncPullOutcome.Ok(response.body())
        response.status == HttpStatusCode.Unauthorized -> SyncPullOutcome.Unauthorized
        else -> {
          LOGGER.w { "Pull failed with ${response.status}" }
          SyncPullOutcome.Error("HTTP ${response.status.value}")
        }
      }
    } catch (e: Exception) {
      LOGGER.w(e) { "Pull threw" }
      SyncPullOutcome.Error(e.message ?: "Pull failed")
    }
  }

  /** Pushes one batch. The caller is responsible for keeping it within the server's row cap. */
  suspend fun push(accessToken: String, request: SyncPushRequest): SyncPushOutcome {
    return try {
      val response: HttpResponse =
        httpClient.post("$baseUrl/sync") {
          bearerAuth(accessToken)
          contentType(ContentType.Application.Json)
          setBody(request)
        }
      when {
        response.status.isSuccess() -> SyncPushOutcome.Ok(response.body())
        response.status == HttpStatusCode.Unauthorized -> SyncPushOutcome.Unauthorized
        response.status == HttpStatusCode.PayloadTooLarge -> SyncPushOutcome.TooLarge
        else -> {
          LOGGER.w { "Push failed with ${response.status}" }
          SyncPushOutcome.Error("HTTP ${response.status.value}")
        }
      }
    } catch (e: Exception) {
      LOGGER.w(e) { "Push threw" }
      SyncPushOutcome.Error(e.message ?: "Push failed")
    }
  }

  companion object {
    /** Deliberately unresolvable; see the class doc. */
    const val DEFAULT_BASE_URL = "https://chess.invalid/v1"
  }
}

/** Outcome of [SyncApiClient.pull]. */
sealed class SyncPullOutcome {
  data class Ok(val response: SyncPullResponse) : SyncPullOutcome()

  /**
   * The access token was rejected. The caller ([SyncEngine]) does not retry here: a fresh
   * [proj.memorchess.axl.core.auth.AuthProvider.accessToken] call already happened before this.
   */
  data object Unauthorized : SyncPullOutcome()

  data class Error(val message: String) : SyncPullOutcome()
}

/** Outcome of [SyncApiClient.push]. */
sealed class SyncPushOutcome {
  data class Ok(val response: SyncPushResponse) : SyncPushOutcome()

  data object Unauthorized : SyncPushOutcome()

  /** The batch exceeded the server's row cap; the caller must have already chunked below it. */
  data object TooLarge : SyncPushOutcome()

  data class Error(val message: String) : SyncPushOutcome()
}

private val LOGGER = Logger.withTag("SyncApiClient")
