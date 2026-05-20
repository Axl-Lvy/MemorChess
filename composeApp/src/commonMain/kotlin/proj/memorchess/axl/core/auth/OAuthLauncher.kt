package proj.memorchess.axl.core.auth

/**
 * Platform side of the OAuth flow: opens [authorizationUrl] in the system browser, waits for the
 * user to be redirected back to [redirectUri], and returns the captured authorization code.
 *
 * Implementations should also validate that the redirect carries the [expectedState] CSRF token.
 *
 * @return the authorization code on success, or a [OAuthLaunchError] describing the failure.
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

/**
 * Lichess client identifier sent in the OAuth authorize request.
 *
 * Lichess accepts any descriptive id for public PKCE clients; no registration is required.
 */
const val LICHESS_CLIENT_ID: String = "memorchess.app"

/**
 * Platform specific redirect URI passed to Lichess.
 *
 * Native platforms use a custom scheme (`memorchess://oauth`). JVM uses a loopback URL. wasm uses
 * the current page origin with a callback path so the popup can navigate back to it.
 */
expect val LICHESS_REDIRECT_URI: String
