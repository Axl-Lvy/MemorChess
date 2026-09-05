package proj.memorchess.axl.core.auth

import co.touchlab.kermit.Logger
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import memorchess.composeapp.generated.resources.Res
import memorchess.composeapp.generated.resources.sync_browser_unavailable
import memorchess.composeapp.generated.resources.sync_missing_code
import memorchess.composeapp.generated.resources.sync_oauth_failed
import memorchess.composeapp.generated.resources.sync_sign_in_failed
import memorchess.composeapp.generated.resources.sync_state_mismatch
import org.jetbrains.compose.resources.StringResource

/**
 * Drives the end to end OIDC sign in and transparent token refresh, mirroring
 * [LichessSignInController]'s orchestration shape.
 */
class OidcSignInController(
  private val launch:
    suspend (
      authorizationUrl: String,
      redirectUri: String,
      expectedState: String,
    ) -> OAuthLaunchResult,
  private val oidcClient: OidcClient,
  private val tokenStore: OidcTokenStore,
  private val redirectUri: String,
  private val clientId: String = SYNC_CLIENT_ID,
  private val audience: String = SYNC_AUDIENCE,
  private val now: () -> Instant = { Clock.System.now() },
) : AuthProvider {

  private val refreshMutex = Mutex()

  /** Convenience constructor binding to the platform [OAuthLauncher]. */
  constructor(
    launcher: OAuthLauncher,
    oidcClient: OidcClient,
    tokenStore: OidcTokenStore,
    redirectUri: String,
    clientId: String = SYNC_CLIENT_ID,
    audience: String = SYNC_AUDIENCE,
  ) : this(
    launch = { url, redirect, state -> launcher.launch(url, redirect, state) },
    oidcClient = oidcClient,
    tokenStore = tokenStore,
    redirectUri = redirectUri,
    clientId = clientId,
    audience = audience,
  )

  override val currentAccount: StateFlow<Account?> = tokenStore.currentAccount

  override suspend fun signIn(): SignInResult {
    val pkce = PkceGenerator.generate()
    val state = generateOidcState()
    val authorizationUrl =
      oidcClient.buildAuthorizationUrl(
        clientId = clientId,
        redirectUri = redirectUri,
        codeChallenge = pkce.challenge,
        state = state,
        audience = audience,
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
    val exchange = oidcClient.exchangeCode(clientId, redirectUri, code, pkce.verifier)
    return when (exchange) {
      is OidcTokenExchangeResult.Ok -> {
        persist(exchange)
        SignInResult.Success
      }
      OidcTokenExchangeResult.Rejected ->
        SignInResult.Failed(Res.string.sync_sign_in_failed, "rejected")
      is OidcTokenExchangeResult.Error ->
        SignInResult.Failed(Res.string.sync_sign_in_failed, exchange.message)
    }
  }

  override fun signOut() {
    tokenStore.clear()
  }

  override suspend fun accessToken(): TokenResult {
    val stored = tokenStore.getAccessToken() ?: return TokenResult.SignedOut
    val expiresAt = tokenStore.getExpiresAt()
    if (expiresAt != null && expiresAt - now() > REFRESH_BUFFER) {
      return TokenResult.Ok(stored)
    }
    return refreshMutex.withLock {
      // Re-check: another caller may have already refreshed while this one waited for the lock.
      val current = tokenStore.getAccessToken() ?: return@withLock TokenResult.SignedOut
      val currentExpiry = tokenStore.getExpiresAt()
      if (currentExpiry != null && currentExpiry - now() > REFRESH_BUFFER) {
        return@withLock TokenResult.Ok(current)
      }
      val refreshToken =
        tokenStore.getRefreshToken()
          ?: run {
            tokenStore.clear()
            return@withLock TokenResult.Failed.Terminal
          }
      when (val exchange = oidcClient.refresh(clientId, refreshToken)) {
        is OidcTokenExchangeResult.Ok -> {
          persist(exchange)
          TokenResult.Ok(exchange.accessToken)
        }
        OidcTokenExchangeResult.Rejected -> {
          tokenStore.clear()
          TokenResult.Failed.Terminal
        }
        is OidcTokenExchangeResult.Error -> {
          LOGGER.w { "Refresh failed transiently: ${exchange.message}" }
          TokenResult.Failed.Transient
        }
      }
    }
  }

  private fun persist(exchange: OidcTokenExchangeResult.Ok) {
    tokenStore.save(
      accessToken = exchange.accessToken,
      refreshToken = exchange.refreshToken,
      expiresAt = now() + exchange.expiresInSeconds.seconds,
      account = exchange.account,
    )
  }

  private fun OAuthLaunchError.toMessage(): StringResource =
    when (this) {
      OAuthLaunchError.MISSING_CODE -> Res.string.sync_missing_code
      OAuthLaunchError.STATE_MISMATCH -> Res.string.sync_state_mismatch
      OAuthLaunchError.BROWSER_UNAVAILABLE -> Res.string.sync_browser_unavailable
      OAuthLaunchError.PLATFORM_ERROR -> Res.string.sync_oauth_failed
    }

  private companion object {
    val REFRESH_BUFFER = 60.seconds
  }
}

private val LOGGER = Logger.withTag("OidcSignInController")
