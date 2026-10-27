package proj.memorchess.axl.core.auth

/**
 * Opens the OAuth authorization URL in the system browser and captures the code from the redirect.
 * Implementations must reject a redirect whose `state` differs from the expected one.
 */
expect class OAuthLauncher() {
  suspend fun launch(
    authorizationUrl: String,
    redirectUri: String,
    expectedState: String,
  ): OAuthLaunchResult
}

/** Outcome of [OAuthLauncher.launch]. */
sealed class OAuthLaunchResult {
  /** User authorized the request; [code] is the authorization code to exchange for a token. */
  data class Ok(val code: String) : OAuthLaunchResult()

  /** User cancelled or closed the browser before authorizing. */
  data object Cancelled : OAuthLaunchResult()

  /** Anything else: missing code, state mismatch, network, platform error. */
  data class Error(val error: OAuthLaunchError) : OAuthLaunchResult()
}

/** Reasons [OAuthLaunchResult.Error] can occur. */
enum class OAuthLaunchError {
  /** The redirect carried no `code` parameter. */
  MISSING_CODE,
  /** The redirect's `state` did not match the one we sent (possible CSRF). */
  STATE_MISMATCH,
  /** Could not open the system browser. */
  BROWSER_UNAVAILABLE,
  /** Implementation specific failure. */
  PLATFORM_ERROR,
}

/** Lichess OAuth client id; Lichess accepts any descriptive id for public PKCE clients. */
const val LICHESS_CLIENT_ID: String = "memorchess.app"

/**
 * Platform specific OAuth redirect URI: a custom scheme on native, a loopback URL on JVM, and the
 * page origin on wasm so the popup can navigate back to it.
 */
expect val LICHESS_REDIRECT_URI: String
