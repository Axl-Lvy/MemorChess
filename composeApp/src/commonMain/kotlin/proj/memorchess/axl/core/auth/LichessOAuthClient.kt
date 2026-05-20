package proj.memorchess.axl.core.auth

import co.touchlab.kermit.Logger
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Speaks OAuth 2.0 + PKCE with `https://lichess.org`.
 *
 * Handles the two HTTP calls the client owns: the access token exchange after Lichess redirects
 * back with an authorization code, and the `/api/account` request used to resolve the signed in
 * user's username for display.
 *
 * The browser side of the flow (showing `lichess.org/oauth?...` and reading the redirect) is
 * platform specific and lives behind [OAuthLauncher].
 */
class LichessOAuthClient(private val httpClient: HttpClient) {

  /**
   * Builds the authorization URL the user must visit to grant access.
   *
   * Lichess accepts the `scope` parameter empty for read only explorer access; we still pass it so
   * the URL is unambiguous.
   */
  fun buildAuthorizationUrl(
    clientId: String,
    redirectUri: String,
    codeChallenge: String,
    state: String,
  ): String =
    "$AUTHORIZE_URL?response_type=code" +
      "&client_id=${clientId.encode()}" +
      "&redirect_uri=${redirectUri.encode()}" +
      "&code_challenge_method=S256" +
      "&code_challenge=${codeChallenge.encode()}" +
      "&scope=" +
      "&state=${state.encode()}"

  /** Exchanges [code] (received from the redirect URI) for an access token via PKCE. */
  suspend fun exchangeCode(
    clientId: String,
    redirectUri: String,
    code: String,
    codeVerifier: String,
  ): TokenExchangeResult {
    return try {
      val response: HttpResponse =
        httpClient.submitForm(
          url = TOKEN_URL,
          formParameters =
            Parameters.build {
              append("grant_type", "authorization_code")
              append("code", code)
              append("redirect_uri", redirectUri)
              append("client_id", clientId)
              append("code_verifier", codeVerifier)
            },
        )
      if (response.status.isSuccess()) {
        val payload: TokenResponse = response.body()
        TokenExchangeResult.Ok(payload.accessToken)
      } else {
        LOGGER.w { "Lichess token exchange failed with ${response.status}" }
        TokenExchangeResult.Error("HTTP ${response.status.value}")
      }
    } catch (e: Exception) {
      LOGGER.w(e) { "Lichess token exchange threw" }
      TokenExchangeResult.Error(e.message ?: "Token exchange failed")
    }
  }

  /** Resolves the username of the account behind [token] via `GET /api/account`. */
  suspend fun fetchAccount(token: String): AccountResult {
    return try {
      val response: HttpResponse = httpClient.get(ACCOUNT_URL) { bearerAuth(token) }
      when {
        response.status.isSuccess() -> {
          val payload: AccountResponse = response.body()
          AccountResult.Ok(payload.username)
        }
        response.status == HttpStatusCode.Unauthorized -> AccountResult.Unauthorized
        else -> AccountResult.Error("HTTP ${response.status.value}")
      }
    } catch (e: Exception) {
      LOGGER.w(e) { "Lichess /api/account threw" }
      AccountResult.Error(e.message ?: "Account fetch failed")
    }
  }

  private fun String.encode(): String = encodeURLComponent(this)

  private companion object {
    const val AUTHORIZE_URL = "https://lichess.org/oauth"
    const val TOKEN_URL = "https://lichess.org/api/token"
    const val ACCOUNT_URL = "https://lichess.org/api/account"
  }
}

/** Outcome of [LichessOAuthClient.exchangeCode]. */
sealed class TokenExchangeResult {
  /** Exchange succeeded; contains the access token. */
  data class Ok(val accessToken: String) : TokenExchangeResult()

  /** Any failure with a human readable [message]. */
  data class Error(val message: String) : TokenExchangeResult()
}

/** Outcome of [LichessOAuthClient.fetchAccount]. */
sealed class AccountResult {
  /** Lookup succeeded; [username] is the Lichess handle. */
  data class Ok(val username: String) : AccountResult()

  /** Token rejected by Lichess; caller should sign out. */
  data object Unauthorized : AccountResult()

  /** Any other failure with a human readable [message]. */
  data class Error(val message: String) : AccountResult()
}

@Serializable
private data class TokenResponse(
  @SerialName("access_token") val accessToken: String,
  @SerialName("token_type") val tokenType: String? = null,
  @SerialName("expires_in") val expiresIn: Long? = null,
)

@Serializable private data class AccountResponse(val username: String)

private val LOGGER = Logger.withTag("LichessOAuthClient")

/**
 * Minimal RFC 3986 percent encoder for query string components.
 *
 * Ktor's URL builder would do this, but here we are concatenating into a URL we hand to the OS
 * browser, which would not run any further normalization.
 */
private fun encodeURLComponent(value: String): String = buildString {
  for (b in value.encodeToByteArray()) {
    val c = b.toInt() and 0xff
    when {
      c in 0x30..0x39 || c in 0x41..0x5a || c in 0x61..0x7a -> append(c.toChar())
      c == '-'.code || c == '_'.code || c == '.'.code || c == '~'.code -> append(c.toChar())
      else -> {
        append('%')
        append(HEX[c ushr 4])
        append(HEX[c and 0x0f])
      }
    }
  }
}

private val HEX = "0123456789ABCDEF".toCharArray()
