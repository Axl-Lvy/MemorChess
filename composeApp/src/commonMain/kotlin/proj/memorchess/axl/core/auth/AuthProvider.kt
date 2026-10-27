package proj.memorchess.axl.core.auth

import kotlinx.coroutines.flow.StateFlow

/**
 * Client identity: sign in and out against the configured OIDC issuer, and hand out access tokens,
 * refreshing them transparently.
 */
interface AuthProvider {

  /** Currently signed in account, or `null` when signed out. */
  val currentAccount: StateFlow<Account?>

  /**
   * Runs the full sign in flow; safe to call repeatedly, the result reflects the latest call. The
   * wasmJs implementation never returns.
   */
  suspend fun signIn(): SignInResult

  /** Clears the stored session locally without calling the issuer. */
  fun signOut()

  /**
   * A valid access token, refreshed first when close to expiry. On [TokenResult.Failed.Terminal]
   * the session is already cleared and [currentAccount] is `null`.
   */
  suspend fun accessToken(): TokenResult
}

/**
 * A signed in account. [sub] is the only stable identifier; [name] is for display, never a join
 * key.
 */
data class Account(val sub: String, val name: String?)

/** Outcome of [AuthProvider.accessToken]. */
sealed class TokenResult {
  /** A valid access token. */
  data class Ok(val accessToken: AccessToken) : TokenResult()

  /** No session is stored at all; the caller was never signed in or has been signed out. */
  data object SignedOut : TokenResult()

  /** Refreshing the token failed. */
  sealed class Failed : TokenResult() {
    /** Network or server error. The session is still valid; retry later. */
    data object Transient : Failed()

    /** The refresh token was rejected by the issuer. The session has been cleared. */
    data object Terminal : Failed()
  }
}
