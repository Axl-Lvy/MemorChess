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
import proj.memorchess.axl.core.auth.AccessToken

/**
 * HTTP client for `:server`'s `/v1/sync`. Every failure maps to a typed outcome, never a thrown
 * exception.
 *
 * @param baseUrl Root URL, without a trailing slash.
 */
class SyncApiClient(
  private val httpClient: HttpClient,
  private val baseUrl: String = DEFAULT_BASE_URL,
) {

  /**
   * Registers [deviceId], which gates both push and pull.
   *
   * @param afterReset Whether this device wiped its synced local state, the only way a device below
   *   the server's collection floor can start over.
   */
  suspend fun registerDevice(
    accessToken: AccessToken,
    deviceId: DeviceId,
    platform: DevicePlatform,
    afterReset: Boolean,
  ): SyncRegisterOutcome {
    return try {
      val response: HttpResponse =
        httpClient.put("$baseUrl/me/devices/${deviceId.value}") {
          bearerAuth(accessToken.value)
          contentType(ContentType.Application.Json)
          setBody(SyncDeviceRegisterRequest(platform.wireName, afterReset))
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
   *   committed none since its last failure.
   */
  suspend fun pull(
    accessToken: AccessToken,
    deviceId: DeviceId,
    ack: String?,
    limit: Int,
  ): SyncPullOutcome {
    return try {
      val response: HttpResponse =
        httpClient.get("$baseUrl/sync") {
          bearerAuth(accessToken.value)
          parameter("device", deviceId.value)
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
  suspend fun push(accessToken: AccessToken, request: SyncPushRequest): SyncPushOutcome {
    return try {
      val response: HttpResponse =
        httpClient.post("$baseUrl/sync") {
          bearerAuth(accessToken.value)
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

  /**
   * Erases every synced row of the caller on the server.
   *
   * @param deviceSeq Sequence number stamped on the erase, from the same counter as pushed rows.
   */
  suspend fun erase(
    accessToken: AccessToken,
    deviceId: DeviceId,
    deviceSeq: Long,
  ): SyncEraseOutcome {
    return try {
      val response: HttpResponse =
        httpClient.post("$baseUrl/me/erase") {
          bearerAuth(accessToken.value)
          contentType(ContentType.Application.Json)
          setBody(SyncEraseRequest(deviceId, deviceSeq))
        }
      when {
        response.status.isSuccess() -> SyncEraseOutcome.Ok(response.body())
        response.status == HttpStatusCode.Unauthorized -> SyncEraseOutcome.Unauthorized
        response.status == HttpStatusCode.TooManyRequests -> SyncEraseOutcome.RateLimited
        response.status == HttpStatusCode.Gone && response.namesResyncRequired() ->
          SyncEraseOutcome.ResyncRequired
        else -> {
          LOGGER.w { "Erase failed with ${response.status}" }
          SyncEraseOutcome.Error("HTTP ${response.status.value}")
        }
      }
    } catch (e: Exception) {
      LOGGER.w(e) { "Erase threw" }
      SyncEraseOutcome.Error(e.message ?: "Erase failed")
    }
  }

  companion object {
    /** Deliberately unresolvable placeholder, so a missing override fails as an error outcome. */
    const val DEFAULT_BASE_URL = "https://chess.invalid/v1"
  }
}

/** Whether the body names [ApiErrorCode.QUOTA_EXCEEDED], `false` if it does not decode. */
private suspend fun HttpResponse.namesQuotaExceeded(): Boolean =
  runCatching { body<ApiError>() }.getOrNull()?.code == ApiErrorCode.QUOTA_EXCEEDED

/** Whether the body names [ApiErrorCode.RESYNC_REQUIRED], `false` if it does not decode. */
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

  /** The access token was rejected even though it was freshly obtained, so it is not retried. */
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
   * The batch would exceed a per user storage quota. Not transient: retrying cannot succeed until
   * the caller frees space.
   */
  data object QuotaExceeded : SyncPushOutcome()

  /** The caller exceeded its request budget. Transient: the caller's own backoff will clear it. */
  data object RateLimited : SyncPushOutcome()

  /** See [SyncRegisterOutcome.ResyncRequired]. */
  data object ResyncRequired : SyncPushOutcome()

  data class Error(val message: String) : SyncPushOutcome()
}

/** Outcome of [SyncApiClient.erase]. */
sealed class SyncEraseOutcome {
  data class Ok(val response: SyncEraseResponse) : SyncEraseOutcome()

  data object Unauthorized : SyncEraseOutcome()

  /** The caller exceeded its request budget. Transient: the caller's own backoff will clear it. */
  data object RateLimited : SyncEraseOutcome()

  /** See [SyncRegisterOutcome.ResyncRequired]. */
  data object ResyncRequired : SyncEraseOutcome()

  data class Error(val message: String) : SyncEraseOutcome()
}

private val LOGGER = Logger.withTag("SyncApiClient")
