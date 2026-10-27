package proj.memorchess.axl.core.auth

import co.touchlab.kermit.Logger
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** Outcome of [decideOidcRedirectExchange]. */
internal sealed class OidcRedirectDecision {
  /** The current path is not the sync redirect callback; ordinary app boot. */
  data object NotACallback : OidcRedirectDecision()

  /**
   * The path is the callback; [cleanedUrl] must replace the address bar before Koin/App see it.
   * [exchange] is `null` on a state mismatch, IdP `error`, missing `code` or no pending record.
   */
  data class Callback(val cleanedUrl: String, val exchange: PendingExchange?) :
    OidcRedirectDecision()
}

/** A `code`/`codeVerifier` pair ready to exchange for tokens. */
internal data class PendingExchange(val code: String, val codeVerifier: String)

/**
 * Decides how to resume a sign in redirect from [currentPath] and the [pending] record.
 * [queryParams] must already be URL-decoded.
 */
internal fun decideOidcRedirectExchange(
  currentPath: String,
  redirectPath: String,
  queryParams: Map<String, String>,
  pending: PendingOidcRedirect?,
): OidcRedirectDecision {
  if (currentPath != redirectPath) return OidcRedirectDecision.NotACallback
  val returnHash = pending?.returnHash.orEmpty()
  val cleanedUrl = "/$returnHash"
  val code = queryParams["code"]
  val state = queryParams["state"]
  val error = queryParams["error"]
  val exchange =
    if (error == null && pending != null && code != null && state == pending.state) {
      PendingExchange(code, pending.codeVerifier)
    } else {
      null
    }
  return OidcRedirectDecision.Callback(cleanedUrl, exchange)
}

/**
 * Exchanges [exchange]'s code for tokens, saves them to [tokenStore] and calls [onSignedIn]. Only
 * logs on failure.
 */
internal suspend fun exchangeOidcRedirectCode(
  oidcClient: OidcClient,
  tokenStore: OidcTokenStore,
  clientId: String,
  redirectUri: String,
  exchange: PendingExchange,
  now: () -> Instant = { Clock.System.now() },
  onSignedIn: () -> Unit,
) {
  when (
    val result =
      oidcClient.exchangeCode(clientId, redirectUri, exchange.code, exchange.codeVerifier)
  ) {
    is OidcTokenExchangeResult.Ok -> {
      tokenStore.save(
        accessToken = result.accessToken,
        refreshToken = result.refreshToken,
        expiresAt = now() + result.expiresInSeconds.seconds,
        account = result.account,
      )
      onSignedIn()
    }
    OidcTokenExchangeResult.Rejected -> LOGGER.w { "Redirect code exchange rejected" }
    is OidcTokenExchangeResult.Error ->
      LOGGER.w { "Redirect code exchange failed: ${result.message}" }
  }
}

private val LOGGER = Logger.withTag("OidcRedirectResumer")
