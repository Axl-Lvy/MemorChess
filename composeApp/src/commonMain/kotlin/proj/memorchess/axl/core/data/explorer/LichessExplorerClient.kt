package proj.memorchess.axl.core.data.explorer

import co.touchlab.kermit.Logger
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ResponseException
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import proj.memorchess.axl.core.date.DateUtil

/**
 * HTTP client for the Lichess Opening Explorer API.
 *
 * Serializes requests with a [Mutex] and enforces a [minGap] between consecutive calls to stay well
 * below Lichess's published rate limits. On HTTP 429 responses, retries up to [maxAttemptsOn429]
 * times with exponential backoff before giving up and returning [ExplorerResult.RateLimited].
 *
 * Returns [ExplorerResult.NetworkError] for any other failure so callers can show a friendly
 * message without inspecting Ktor exceptions.
 */
class LichessExplorerClient(
  private val httpClient: HttpClient,
  private val tokenProvider: () -> String? = { null },
  private val minGap: Duration = DEFAULT_MIN_GAP,
  private val maxAttemptsOn429: Int = DEFAULT_MAX_ATTEMPTS_ON_429,
) {

  private val mutex = Mutex()
  private var lastRequestEpochMs: Long = 0L

  /**
   * Fetches the opening explorer response for [fen] from [source].
   *
   * @param plies Maximum number of moves to include in the response, capped server side at 12.
   */
  suspend fun fetch(
    source: ExplorerSource,
    fen: String,
    plies: Int = DEFAULT_PLIES,
  ): ExplorerResult = mutex.withLock {
    throttle()
    runRequest(source, fen, plies, attempt = 1)
  }

  private suspend fun runRequest(
    source: ExplorerSource,
    fen: String,
    plies: Int,
    attempt: Int,
  ): ExplorerResult {
    val url = "$BASE_URL/${source.path}"
    val token = tokenProvider()
    if (token == null) {
      LOGGER.w { "No Lichess token available, cannot call $url" }
      return ExplorerResult.Unauthorized
    }
    return try {
      val response: HttpResponse =
        httpClient.get(url) {
          header(HttpHeaders.UserAgent, USER_AGENT)
          bearerAuth(token)
          parameter("fen", fen)
          parameter("moves", plies)
          parameter("topGames", 0)
          if (source == ExplorerSource.LICHESS) {
            // Restrict the Lichess online corpus to the strongest rating bucket. Masters does not
            // accept this parameter.
            parameter("ratings", "2500")
          }
        }
      lastRequestEpochMs = nowEpochMs()
      when {
        response.status.isSuccess() -> ExplorerResult.Ok(response.body())
        response.status == HttpStatusCode.TooManyRequests -> retryOn429(source, fen, plies, attempt)
        response.status == HttpStatusCode.Unauthorized -> {
          LOGGER.w { "Lichess explorer returned 401 for $url fen=$fen" }
          ExplorerResult.Unauthorized
        }
        else -> {
          LOGGER.w { "Lichess explorer returned ${response.status} for $url fen=$fen" }
          ExplorerResult.NetworkError("HTTP ${response.status.value}")
        }
      }
    } catch (e: ResponseException) {
      when (e.response.status) {
        HttpStatusCode.TooManyRequests -> retryOn429(source, fen, plies, attempt)
        HttpStatusCode.Unauthorized -> ExplorerResult.Unauthorized
        else -> {
          LOGGER.w(e) { "Lichess explorer request failed for $url fen=$fen" }
          ExplorerResult.NetworkError(e.message ?: "Request failed")
        }
      }
    } catch (e: Exception) {
      LOGGER.w(e) { "Lichess explorer request failed for $url fen=$fen" }
      ExplorerResult.NetworkError(e.message ?: "Request failed")
    }
  }

  private suspend fun retryOn429(
    source: ExplorerSource,
    fen: String,
    plies: Int,
    attempt: Int,
  ): ExplorerResult {
    if (attempt >= maxAttemptsOn429) {
      LOGGER.w { "Giving up after $attempt rate limited attempts for $fen" }
      return ExplorerResult.RateLimited
    }
    val backoff = INITIAL_BACKOFF * (1 shl (attempt - 1))
    LOGGER.i { "Rate limited by Lichess, retry in $backoff (attempt $attempt)" }
    delay(backoff)
    return runRequest(source, fen, plies, attempt + 1)
  }

  private suspend fun throttle() {
    val now = nowEpochMs()
    val sinceLast = (now - lastRequestEpochMs).milliseconds
    if (sinceLast < minGap) {
      delay(minGap - sinceLast)
    }
  }

  private fun nowEpochMs(): Long = DateUtil.now().toEpochMilliseconds()

  companion object {
    private const val BASE_URL = "https://explorer.lichess.ovh"
    private const val DEFAULT_PLIES = 12

    /**
     * Identifies MemorChess to the Lichess explorer service. The Lichess team asks every API client
     * to set a descriptive User-Agent so they can contact maintainers if something goes wrong;
     * their CDN rejects requests with a generic library User-Agent on the explorer host.
     */
    private const val USER_AGENT = "MemorChess (https://github.com/Axl-Lvy/MemorChess)"

    /** Minimum interval between consecutive requests. */
    val DEFAULT_MIN_GAP: Duration = 250.milliseconds

    /** Initial backoff applied after the first HTTP 429 response. */
    val INITIAL_BACKOFF: Duration = 500.milliseconds

    /** Maximum number of attempts before returning [ExplorerResult.RateLimited]. */
    const val DEFAULT_MAX_ATTEMPTS_ON_429: Int = 3
  }
}

/** Result of a [LichessExplorerClient.fetch] call. */
sealed class ExplorerResult {
  /** Request succeeded. */
  data class Ok(val response: LichessExplorerResponse) : ExplorerResult()

  /** Lichess returned HTTP 429 and the retry budget was exhausted. */
  data object RateLimited : ExplorerResult()

  /**
   * No token available or token rejected by Lichess (HTTP 401).
   *
   * The explorer requires OAuth authentication since Lichess gated `explorer.lichess.ovh` behind
   * sign in. Callers should prompt the user to sign in.
   */
  data object Unauthorized : ExplorerResult()

  /** Any other failure (network, parse, server error). */
  data class NetworkError(val message: String) : ExplorerResult()
}

private val LOGGER = Logger.withTag("LichessExplorerClient")
