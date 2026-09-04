package proj.memorchess.axl.core.auth

import co.touchlab.kermit.Logger
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.forms.submitForm
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.http.isSuccess
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Speaks OAuth 2.0 + PKCE + OpenID Connect with [issuer].
 *
 * Handles the two token endpoint calls the client owns (the initial code exchange and refresh)
 * plus decoding the id token for display. The browser side of the flow is platform specific and
 * lives behind [OAuthLauncher], reused unchanged from the Lichess flow.
 */
class OidcClient(private val httpClient: HttpClient, private val issuer: String = SYNC_ISSUER) {

  /** Builds the authorization URL the user must visit to grant access. */
  fun buildAuthorizationUrl(
    clientId: String,
    redirectUri: String,
    codeChallenge: String,
    state: String,
    audience: String = SYNC_AUDIENCE,
  ): String =
    "$issuer/auth?response_type=code" +
      "&client_id=${clientId.encode()}" +
      "&redirect_uri=${redirectUri.encode()}" +
      "&code_challenge_method=S256" +
      "&code_challenge=${codeChallenge.encode()}" +
      "&scope=${"openid offline_access profile".encode()}" +
      "&resource=${audience.encode()}" +
      "&state=${state.encode()}"

  /** Exchanges [code] (received from the redirect URI) for a token set via PKCE. */
  suspend fun exchangeCode(
    clientId: String,
    redirectUri: String,
    code: String,
    codeVerifier: String,
  ): OidcTokenExchangeResult =
    tokenRequest(
      Parameters.build {
        append("grant_type", "authorization_code")
        append("code", code)
        append("redirect_uri", redirectUri)
        append("client_id", clientId)
        append("code_verifier", codeVerifier)
      }
    )

  /**
   * Exchanges a stored refresh token for a new token set. The issuer rotates refresh tokens for
   * public clients: the caller must persist the returned [OidcTokenExchangeResult.Ok.refreshToken],
   * not reuse [refreshToken].
   */
  suspend fun refresh(clientId: String, refreshToken: String): OidcTokenExchangeResult =
    tokenRequest(
      Parameters.build {
        append("grant_type", "refresh_token")
        append("refresh_token", refreshToken)
        append("client_id", clientId)
      }
    )

  private suspend fun tokenRequest(parameters: Parameters): OidcTokenExchangeResult {
    return try {
      val response: HttpResponse =
        httpClient.submitForm(url = "$issuer/token", formParameters = parameters)
      when {
        response.status.isSuccess() -> {
          val payload: OidcTokenResponse = response.body()
          OidcTokenExchangeResult.Ok(
            accessToken = payload.accessToken,
            refreshToken = payload.refreshToken,
            expiresInSeconds = payload.expiresIn,
            account = payload.idToken?.let(::decodeIdToken),
          )
        }
        response.status == HttpStatusCode.BadRequest -> {
          LOGGER.w { "Token request rejected with ${response.status}" }
          OidcTokenExchangeResult.Rejected
        }
        else -> {
          LOGGER.w { "Token request failed with ${response.status}" }
          OidcTokenExchangeResult.Error("HTTP ${response.status.value}")
        }
      }
    } catch (e: Exception) {
      LOGGER.w(e) { "Token request threw" }
      OidcTokenExchangeResult.Error(e.message ?: "Token request failed")
    }
  }

  private fun String.encode(): String = encodeURLComponent(this)
}

/** Outcome of [OidcClient.exchangeCode] and [OidcClient.refresh]. */
sealed class OidcTokenExchangeResult {
  /** Exchange succeeded. [refreshToken] is `null` only if the request did not ask for one. */
  data class Ok(
    val accessToken: String,
    val refreshToken: String?,
    val expiresInSeconds: Long,
    val account: Account?,
  ) : OidcTokenExchangeResult()

  /** The issuer rejected the request itself (HTTP 400, e.g. an invalid or expired grant). */
  data object Rejected : OidcTokenExchangeResult()

  /** Any other failure: a non-400 HTTP status, or a network/parse exception. */
  data class Error(val message: String) : OidcTokenExchangeResult()
}

@Serializable
private data class OidcTokenResponse(
  @SerialName("access_token") val accessToken: String,
  @SerialName("refresh_token") val refreshToken: String? = null,
  @SerialName("expires_in") val expiresIn: Long,
  @SerialName("id_token") val idToken: String? = null,
)

@Serializable private data class IdTokenPayload(val sub: String, val name: String? = null)

/**
 * Decodes the `sub` and `name` claims out of an id token's payload segment, for display only. The
 * signature is not verified: the token just travelled over TLS straight from the issuer's own
 * token endpoint, so there is nothing to verify against here that the transport did not already
 * guarantee.
 */
@OptIn(ExperimentalEncodingApi::class)
internal fun decodeIdToken(idToken: String): Account? {
  val segments = idToken.split(".")
  if (segments.size != 3) return null
  return try {
    val json =
      Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).decode(segments[1]).decodeToString()
    val payload = ID_TOKEN_JSON.decodeFromString<IdTokenPayload>(json)
    Account(sub = payload.sub, name = payload.name)
  } catch (e: Exception) {
    LOGGER.w(e) { "Could not decode id token" }
    null
  }
}

private val LOGGER = Logger.withTag("OidcClient")

private val ID_TOKEN_JSON = Json { ignoreUnknownKeys = true }
