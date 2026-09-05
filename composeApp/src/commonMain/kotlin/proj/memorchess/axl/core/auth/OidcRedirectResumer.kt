package proj.memorchess.axl.core.auth

import co.touchlab.kermit.Logger
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * Outcome of [decideOidcRedirectExchange]: what the caller's synchronous cleanup step should do.
 */
internal sealed class OidcRedirectDecision {
  /** The current path is not the sync redirect callback at all; ordinary app boot. */
  data object NotACallback : OidcRedirectDecision()

  /**
   * The path is the callback. [cleanedUrl] is what the browser's address bar should become, before
   * Koin/App ever see the callback path. [exchange] is non-null only when a token exchange should
   * follow; every other case (state mismatch, IdP `error`, no pending record, missing `code`) still
   * cleans the URL but performs no exchange.
   */
  data class Callback(val cleanedUrl: String, val exchange: PendingExchange?) :
    OidcRedirectDecision()
}

/** A `code`/`codeVerifier` pair ready to exchange for tokens. */
internal data class PendingExchange(val code: String, val codeVerifier: String)

/**
 * Pure decision for what to do with [currentPath]/[queryParams] against [redirectPath] and whatever
 * [pending] record was stored before navigating away. No I/O: [queryParams] must already be
 * URL-decoded by the caller.
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
 * Exchanges [exchange]'s code for tokens and persists them via [tokenStore], calling [onSignedIn]
 * on success (wired to [proj.memorchess.axl.core.sync.SyncEngine.syncNow] so a redirect sign in
 * triggers a sync, matching the popup flow's own behavior in `SyncAccountSection`). Logs and does
 * nothing further on failure; the caller has already cleared the pending record regardless of
 * outcome.
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
