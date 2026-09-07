package proj.memorchess.axl.core.data.repertoire

import co.touchlab.kermit.Logger
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.serialization.Serializable
import proj.memorchess.axl.core.sync.ApiError
import proj.memorchess.axl.core.sync.ApiErrorCode

/**
 * HTTP client for `:server`'s authenticated repertoire publish surface, mirroring
 * [proj.memorchess.axl.core.sync.SyncApiClient]'s typed outcome pattern: every failure maps to a
 * sealed outcome, never a thrown Ktor exception.
 *
 * @param baseUrl Root URL of `/v1/repertoires`, without a trailing slash. Koin wires the real
 *   deployed server here; [DEFAULT_BASE_URL] is a deliberately unresolvable placeholder for callers
 *   that construct this client directly, same convention as [RepertoireCatalogClient]'s own
 *   default.
 */
class RepertoirePublishClient(
  private val httpClient: HttpClient,
  private val baseUrl: String = DEFAULT_BASE_URL,
) {

  /** Publishes or republishes [id] as the caller's own repertoire. */
  suspend fun publish(
    accessToken: String,
    id: String,
    title: String,
    description: String,
    side: String,
    pgn: String,
  ): PublishOutcome =
    try {
      val response: HttpResponse =
        httpClient.post(baseUrl) {
          bearerAuth(accessToken)
          contentType(ContentType.Application.Json)
          setBody(PublishRequestBody(id, title, description, side, pgn))
        }
      when {
        response.status == HttpStatusCode.Created -> PublishOutcome.Published(response.body())
        response.status == HttpStatusCode.Unauthorized -> PublishOutcome.Unauthorized
        response.status == HttpStatusCode.TooManyRequests -> PublishOutcome.RateLimited
        response.status == HttpStatusCode.PayloadTooLarge ->
          PublishOutcome.PayloadTooLarge(response.errorMessage())
        response.status == HttpStatusCode.BadRequest ->
          PublishOutcome.InvalidPayload(response.errorMessage())
        response.status == HttpStatusCode.Forbidden && response.namesCode(ApiErrorCode.REMOVED) ->
          PublishOutcome.RemovedOnServer
        response.status == HttpStatusCode.Forbidden &&
          response.namesCode(ApiErrorCode.QUOTA_EXCEEDED) ->
          PublishOutcome.QuotaExceeded(response.errorMessage())
        response.status == HttpStatusCode.Forbidden -> PublishOutcome.Forbidden
        else -> {
          LOGGER.w { "Publish of $id failed with ${response.status}" }
          PublishOutcome.Failed(response.errorMessage())
        }
      }
    } catch (e: Exception) {
      LOGGER.w(e) { "Publish of $id threw" }
      PublishOutcome.Failed(e.message ?: "Publish failed")
    }

  /** Removes [id], the caller's own repertoire. */
  suspend fun remove(accessToken: String, id: String): RemoveOutcome =
    try {
      val response: HttpResponse = httpClient.delete("$baseUrl/$id") { bearerAuth(accessToken) }
      when {
        response.status == HttpStatusCode.NoContent -> RemoveOutcome.Removed
        response.status == HttpStatusCode.NotFound -> RemoveOutcome.NotFound
        response.status == HttpStatusCode.Unauthorized -> RemoveOutcome.Unauthorized
        response.status == HttpStatusCode.TooManyRequests -> RemoveOutcome.RateLimited
        response.status == HttpStatusCode.Forbidden -> RemoveOutcome.Forbidden
        else -> {
          LOGGER.w { "Remove of $id failed with ${response.status}" }
          RemoveOutcome.Failed(response.errorMessage())
        }
      }
    } catch (e: Exception) {
      LOGGER.w(e) { "Remove of $id threw" }
      RemoveOutcome.Failed(e.message ?: "Remove failed")
    }

  companion object {
    /** Deliberately unresolvable; see the class doc. */
    const val DEFAULT_BASE_URL = "https://chess.invalid/v1/repertoires"
  }
}

@Serializable
private data class PublishRequestBody(
  val id: String,
  val title: String,
  val description: String,
  val side: String,
  val pgn: String,
)

/**
 * Whether this response's body names [code], defaulting to `false` on a body that fails to decode.
 */
private suspend fun HttpResponse.namesCode(code: String): Boolean =
  runCatching { body<ApiError>() }.getOrNull()?.code == code

/**
 * This response's error message, or a generic fallback when the body fails to decode as [ApiError].
 */
private suspend fun HttpResponse.errorMessage(): String =
  runCatching { body<ApiError>() }.getOrNull()?.message ?: "HTTP ${status.value}"

/** Outcome of [RepertoirePublishClient.publish]. */
sealed class PublishOutcome {
  data class Published(val descriptor: RepertoireDescriptor) : PublishOutcome()

  data class InvalidPayload(val reason: String) : PublishOutcome()

  data class PayloadTooLarge(val reason: String) : PublishOutcome()

  /** This id belongs to a different author. */
  data object Forbidden : PublishOutcome()

  /** This id was permanently removed by its author or a moderator and can never be republished. */
  data object RemovedOnServer : PublishOutcome()

  data class QuotaExceeded(val reason: String) : PublishOutcome()

  data object Unauthorized : PublishOutcome()

  data object RateLimited : PublishOutcome()

  data class Failed(val reason: String) : PublishOutcome()
}

/** Outcome of [RepertoirePublishClient.remove]. */
sealed class RemoveOutcome {
  data object Removed : RemoveOutcome()

  data object NotFound : RemoveOutcome()

  data object Forbidden : RemoveOutcome()

  data object Unauthorized : RemoveOutcome()

  data object RateLimited : RemoveOutcome()

  data class Failed(val reason: String) : RemoveOutcome()
}

private val LOGGER = Logger.withTag("RepertoirePublishClient")
