package proj.memorchess.axl.core.auth

import co.touchlab.kermit.Logger
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Drives the end to end OAuth sign in:
 *
 * 1. Generate a PKCE pair and a random `state` value.
 * 2. Hand the authorization URL to [OAuthLauncher] and wait for the redirect.
 * 3. Exchange the authorization code for an access token via [LichessOAuthClient].
 * 4. Persist the token, then fetch the username and persist it too.
 *
 * The caller (UI) only sees a [SignInResult] and a token store flow it can observe.
 */
class LichessSignInController(
  private val launch:
    suspend (
      authorizationUrl: String, redirectUri: String, expectedState: String,
    ) -> OAuthLaunchResult,
  private val oauthClient: LichessOAuthClient,
  private val tokenStore: OAuthTokenStore,
  private val redirectUri: String,
  private val clientId: String = LICHESS_CLIENT_ID,
) {

  /** Convenience constructor binding to the platform [OAuthLauncher]. */
  constructor(
    launcher: OAuthLauncher,
    oauthClient: LichessOAuthClient,
    tokenStore: OAuthTokenStore,
    redirectUri: String,
    clientId: String = LICHESS_CLIENT_ID,
  ) : this(
    launch = { url, redirect, state -> launcher.launch(url, redirect, state) },
    oauthClient = oauthClient,
    tokenStore = tokenStore,
    redirectUri = redirectUri,
    clientId = clientId,
  )

  /** Runs the full sign in flow. Safe to invoke multiple times; the result reflects the latest. */
  suspend fun signIn(): SignInResult {
    val pkce = PkceGenerator.generate()
    val state = generateState()
    val authorizationUrl =
      oauthClient.buildAuthorizationUrl(
        clientId = clientId,
        redirectUri = redirectUri,
        codeChallenge = pkce.challenge,
        state = state,
      )
    val launchResult = launch(authorizationUrl, redirectUri, state)
    val code =
      when (launchResult) {
        is OAuthLaunchResult.Ok -> launchResult.code
        OAuthLaunchResult.Cancelled -> return SignInResult.Cancelled
        is OAuthLaunchResult.Error -> {
          LOGGER.w { "OAuth launch failed: ${launchResult.error}" }
          return SignInResult.Failed(launchResult.error.toMessage())
        }
      }
    val exchange =
      oauthClient.exchangeCode(
        clientId = clientId,
        redirectUri = redirectUri,
        code = code,
        codeVerifier = pkce.verifier,
      )
    val token =
      when (exchange) {
        is TokenExchangeResult.Ok -> exchange.accessToken
        is TokenExchangeResult.Error -> return SignInResult.Failed(exchange.message)
      }
    tokenStore.save(token, username = null)
    val account = oauthClient.fetchAccount(token)
    when (account) {
      is AccountResult.Ok -> tokenStore.setUsername(account.username)
      AccountResult.Unauthorized -> {
        tokenStore.clear()
        return SignInResult.Failed("Lichess rejected the token immediately after issuing it")
      }
      is AccountResult.Error -> {
        LOGGER.w { "Could not fetch account after sign in: ${account.message}" }
      }
    }
    return SignInResult.Success
  }

  /** Clears the stored token. Lichess does not require a revoke call for personal tokens. */
  fun signOut() {
    tokenStore.clear()
  }

  @OptIn(ExperimentalEncodingApi::class)
  private fun generateState(): String =
    Base64.UrlSafe.encode(randomBytes(STATE_BYTE_LENGTH)).trimEnd('=')

  private fun OAuthLaunchError.toMessage(): String =
    when (this) {
      OAuthLaunchError.MISSING_CODE -> "Lichess did not return an authorization code"
      OAuthLaunchError.STATE_MISMATCH -> "OAuth state mismatch (possible CSRF)"
      OAuthLaunchError.BROWSER_UNAVAILABLE -> "Could not open the browser"
      OAuthLaunchError.PLATFORM_ERROR -> "OAuth flow failed"
    }

  private companion object {
    const val STATE_BYTE_LENGTH = 32
  }
}

/** Outcome of [LichessSignInController.signIn]. */
sealed class SignInResult {
  /** Sign in completed; the token is now in the [OAuthTokenStore]. */
  data object Success : SignInResult()

  /** User dismissed the browser before authorizing. */
  data object Cancelled : SignInResult()

  /** Sign in failed; [message] is suitable for display. */
  data class Failed(val message: String) : SignInResult()
}

private val LOGGER = Logger.withTag("LichessSignInController")
