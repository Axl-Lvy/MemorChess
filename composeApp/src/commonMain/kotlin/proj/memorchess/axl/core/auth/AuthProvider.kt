package proj.memorchess.axl.core.auth

import kotlinx.coroutines.flow.StateFlow

/**
 * Client identity: sign in and out against the configured OIDC issuer, and hand out access
 * tokens, refreshing them transparently.
 */
interface AuthProvider {

  /** Currently signed in account, or `null` when signed out. */
  val currentAccount: StateFlow<Account?>

  /** Runs the full sign in flow. Safe to invoke multiple times; the result reflects the latest. */
  suspend fun signIn(): SignInResult

  /** Clears the stored session locally. The issuer is not called; there is nothing to revoke. */
  fun signOut()

  /**
   * A valid access token, refreshing it first if it is close to expiry.
   *
   * On [TokenResult.Failed.Terminal] the session has already been cleared and [currentAccount] is
   * now `null`; the caller does not need to call [signOut] itself.
   */
  suspend fun accessToken(): TokenResult
}

/**
 * A signed in account. [sub] is the only stable identifier; [name] is decoded from the id token
 * for display only and is never a join key (see the parent spec, "`sub` is the identity, never
 * the email").
 */
data class Account(val sub: String, val name: String?)

/** Outcome of [AuthProvider.accessToken]. */
sealed class TokenResult {
  /** A valid access token. */
  data class Ok(val accessToken: String) : TokenResult()

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
