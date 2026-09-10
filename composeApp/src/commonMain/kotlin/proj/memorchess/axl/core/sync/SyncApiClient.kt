package proj.memorchess.axl.core.sync

import co.touchlab.kermit.Logger
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
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
 * @param baseUrl Root URL, without a trailing slash. Koin wires the real deployed server here (see
 *   `SYNC_BASE_URL`); [DEFAULT_BASE_URL] is a deliberately unresolvable placeholder for callers
 *   that construct this client directly without one, same convention as
 *   `RepertoireCatalogClient.DEFAULT_BASE_URL`, so a forgotten override degrades to
 *   [SyncPullOutcome.Error]/[SyncPushOutcome.Error] rather than pretending to succeed.
 */
class SyncApiClient(
  private val httpClient: HttpClient,
  private val baseUrl: String = DEFAULT_BASE_URL,
) {

  /**
   * Registers [deviceId], which gates both push and pull.
   *
   * @param afterReset Reports that this device has wiped its synced local state, which is the only
   *   thing that lets one below the server's collection floor start over.
   */
  suspend fun registerDevice(
    accessToken: String,
    deviceId: String,
    platform: String,
    afterReset: Boolean,
  ): SyncRegisterOutcome {
    return try {
      val response: HttpResponse =
        httpClient.put("$baseUrl/me/devices/$deviceId") {
          bearerAuth(accessToken)
          contentType(ContentType.Application.Json)
          setBody(SyncDeviceRegisterRequest(platform, afterReset))
        }
      when {
        response.status.isSuccess() -> SyncRegisterOutcome.Ok
        response.status == HttpStatusCode.Unauthorized -> SyncRegisterOutcome.Unauthorized
        response.status == HttpStatusCode.TooManyRequests -> SyncRegisterOutcome.RateLimited
        response.status == HttpStatusCode.Gone && response.namesResyncRequired() ->
          SyncRegisterOutcome.ResyncRequired
        else -> {
          LOGGER.w { "Register failed with ${response.status}" }
          SyncRegisterOutcome.Error("HTTP ${response.status.value}")
        }
      }
    } catch (e: Exception) {
      LOGGER.w(e) { "Register threw" }
      SyncRegisterOutcome.Error(e.message ?: "Register failed")
    }
  }

  /**
   * Pulls the next page for [deviceId], capped at [limit].
   *
   * @param ack Token of the page this device has just written locally, or `null` when it has
   *   committed none since its last failure. The server keeps the position, so this is the whole of
   *   the caller's contribution to it.
   */
  suspend fun pull(
    accessToken: String,
    deviceId: String,
    ack: String?,
    limit: Int,
  ): SyncPullOutcome {
    return try {
      val response: HttpResponse =
        httpClient.get("$baseUrl/sync") {
          bearerAuth(accessToken)
          parameter("device", deviceId)
          if (ack != null) parameter("ack", ack)
          parameter("limit", limit)
        }
      when {
        response.status.isSuccess() -> SyncPullOutcome.Ok(response.body())
        response.status == HttpStatusCode.Unauthorized -> SyncPullOutcome.Unauthorized
        response.status == HttpStatusCode.TooManyRequests -> SyncPullOutcome.RateLimited
        response.status == HttpStatusCode.Gone && response.namesResyncRequired() ->
          SyncPullOutcome.ResyncRequired
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
        response.status == HttpStatusCode.TooManyRequests -> SyncPushOutcome.RateLimited
        response.status == HttpStatusCode.Forbidden && response.namesQuotaExceeded() ->
          SyncPushOutcome.QuotaExceeded
        response.status == HttpStatusCode.Gone && response.namesResyncRequired() ->
          SyncPushOutcome.ResyncRequired
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

/**
 * Whether this 403 response's body names [ApiErrorCode.QUOTA_EXCEEDED].
 *
 * A 403 is not unambiguous the way 401/413 are on this endpoint: [ApiError]'s own doc says a client
 * branches on [ApiError.code], never on status alone. A body that fails to decode as [ApiError] is
 * treated as not naming it, same as any other cause `push` cannot otherwise attribute.
 */
private suspend fun HttpResponse.namesQuotaExceeded(): Boolean =
  runCatching { body<ApiError>() }.getOrNull()?.code == ApiErrorCode.QUOTA_EXCEEDED

/**
 * Whether this 410 response's body names [ApiErrorCode.RESYNC_REQUIRED].
 *
 * Same reasoning as [namesQuotaExceeded]: a client branches on [ApiError.code], never on status
 * alone, and a body that fails to decode is treated as not naming it.
 */
private suspend fun HttpResponse.namesResyncRequired(): Boolean =
  runCatching { body<ApiError>() }.getOrNull()?.code == ApiErrorCode.RESYNC_REQUIRED

/** Outcome of [SyncApiClient.registerDevice]. */
sealed class SyncRegisterOutcome {
  data object Ok : SyncRegisterOutcome()

  data object Unauthorized : SyncRegisterOutcome()

  /** The caller exceeded its request budget. Transient: the caller's own backoff will clear it. */
  data object RateLimited : SyncRegisterOutcome()

  /**
   * This device was removed and has fallen below what the server already collected. It must wipe
   * its synced local state and register again reporting the wipe.
   */
  data object ResyncRequired : SyncRegisterOutcome()

  data class Error(val message: String) : SyncRegisterOutcome()
}

/** Outcome of [SyncApiClient.pull]. */
sealed class SyncPullOutcome {
  data class Ok(val response: SyncPullResponse) : SyncPullOutcome()

  /**
   * The access token was rejected. The caller ([SyncEngine]) does not retry here: a fresh
   * [proj.memorchess.axl.core.auth.AuthProvider.accessToken] call already happened before this.
   */
  data object Unauthorized : SyncPullOutcome()

  /** The caller exceeded its request budget. Transient: the caller's own backoff will clear it. */
  data object RateLimited : SyncPullOutcome()

  /** See [SyncRegisterOutcome.ResyncRequired]. */
  data object ResyncRequired : SyncPullOutcome()

  data class Error(val message: String) : SyncPullOutcome()
}

/** Outcome of [SyncApiClient.push]. */
sealed class SyncPushOutcome {
  data class Ok(val response: SyncPushResponse) : SyncPushOutcome()

  data object Unauthorized : SyncPushOutcome()

  /** The batch exceeded the server's row cap; the caller must have already chunked below it. */
  data object TooLarge : SyncPushOutcome()

  /**
   * The batch would push the caller past a per user storage quota. Not transient: retrying the same
   * batch can never succeed until the caller frees space, so [SyncEngine] pauses rather than
   * backing off.
   */
  data object QuotaExceeded : SyncPushOutcome()

  /** The caller exceeded its request budget. Transient: the caller's own backoff will clear it. */
  data object RateLimited : SyncPushOutcome()

  /** See [SyncRegisterOutcome.ResyncRequired]. */
  data object ResyncRequired : SyncPushOutcome()

  data class Error(val message: String) : SyncPushOutcome()
}

private val LOGGER = Logger.withTag("SyncApiClient")
