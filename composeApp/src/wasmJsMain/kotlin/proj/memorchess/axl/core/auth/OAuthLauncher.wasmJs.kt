@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package proj.memorchess.axl.core.auth

import kotlin.time.Duration.Companion.minutes
import kotlinx.browser.window
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Web OAuth launcher.
 *
 * Opens [authorizationUrl] in a popup window, then waits for the callback page it eventually
 * navigates to (served at [redirectUri]) to broadcast its URL on a `BroadcastChannel`; see
 * `LICHESS_OAUTH_CALLBACK_BODY` in `:server`. This does not poll `popup.location.href` or
 * `popup.closed`, and the callback page does not post through `window.opener`: on Chrome, the popup
 * gets detached from its opener once it navigates to `lichess.org`, so neither `popup.closed` nor
 * `window.opener` can be trusted afterwards, even once the popup navigates back to our own origin.
 * A polling implementation built on `popup.closed` reported every sign in as cancelled within
 * ~150ms, well before the user could authorize. `BroadcastChannel` is scoped by origin rather than
 * by window reference, so it is unaffected. One consequence: since we no longer watch
 * `popup.closed`, a user who closes the popup by hand before authorizing is only noticed once
 * [SIGN_IN_TIMEOUT] elapses, rather than immediately.
 *
 * The popup approach was picked over a full page redirect to keep the OAuth flow self contained in
 * one suspend call. Some browsers block popups unless the call originates from a click handler.
 */
actual class OAuthLauncher {

  actual suspend fun launch(
    authorizationUrl: String,
    redirectUri: String,
    expectedState: String,
  ): OAuthLaunchResult {
    installMessageListener(redirectUri, expectedState)
    try {
      val popup =
        window.open(authorizationUrl, "memorchess_oauth", "popup=yes,width=600,height=700")
          ?: return OAuthLaunchResult.Error(OAuthLaunchError.BROWSER_UNAVAILABLE)
      val result =
        withTimeoutOrNull(SIGN_IN_TIMEOUT) {
          while (true) {
            val href = takeMatchedHref()
            if (href != null) {
              return@withTimeoutOrNull parseRedirect(href, expectedState)
            }
            delay(POLL_INTERVAL_MS)
          }
          @Suppress("UNREACHABLE_CODE") OAuthLaunchResult.Error(OAuthLaunchError.PLATFORM_ERROR)
        } ?: OAuthLaunchResult.Error(OAuthLaunchError.PLATFORM_ERROR)
      if (!popup.closed) popup.close()
      return result
    } finally {
      uninstallMessageListener()
    }
  }

  private fun parseRedirect(href: String, expectedState: String): OAuthLaunchResult {
    val queryStart = href.indexOf('?')
    if (queryStart < 0) return OAuthLaunchResult.Error(OAuthLaunchError.MISSING_CODE)
    val params =
      href
        .substring(queryStart + 1)
        .split('&')
        .mapNotNull {
          val idx = it.indexOf('=')
          if (idx <= 0) null else it.substring(0, idx) to decodeUriComponent(it.substring(idx + 1))
        }
        .toMap()
    val code = params["code"]
    val state = params["state"]
    return when {
      code == null -> OAuthLaunchResult.Error(OAuthLaunchError.MISSING_CODE)
      state != expectedState -> OAuthLaunchResult.Error(OAuthLaunchError.STATE_MISMATCH)
      else -> OAuthLaunchResult.Ok(code)
    }
  }

  private companion object {
    val SIGN_IN_TIMEOUT = 5.minutes
    const val POLL_INTERVAL_MS = 200L
  }
}

/**
 * Opens the `BroadcastChannel` that receives the callback page's own URL, keeping only the one
 * whose href starts with [redirectUri] and carries [expectedState]. Filtering on the state too (not
 * just the path) matters because the channel is shared across every concurrent attempt on the page:
 * without it, a message left over from an older attempt (for example a popup the user is still
 * authorizing in after starting a second sign in) could be mistaken for the current attempt's
 * answer instead of being ignored, since [parseRedirect] only rejects a state mismatch after the
 * fact. The channel name (`memorchess-oauth`) is kept in sync with the server's own copy of it
 * (`BROADCAST_CHANNEL_NAME` in `StaticFrontendRoutes.kt`) by hand, not by shared code.
 * [takeMatchedHref] drains what it captured; [uninstallMessageListener] tears it down.
 */
private fun installMessageListener(redirectUri: String, expectedState: String): Unit =
  js(
    """{
      globalThis.__memorchessOAuthHref = null;
      globalThis.__memorchessOAuthChannel = new BroadcastChannel('memorchess-oauth');
      globalThis.__memorchessOAuthChannel.onmessage = function(event) {
        var href = event.data && event.data.href;
        if (
          typeof href === 'string' &&
          href.indexOf(redirectUri) === 0 &&
          href.indexOf('state=' + expectedState) > 0
        ) {
          globalThis.__memorchessOAuthHref = href;
        }
      };
    }"""
  )

/**
 * Returns and clears the href captured by [installMessageListener], or `null` if none arrived yet.
 */
private fun takeMatchedHref(): String? =
  js(
    """{
      var href = globalThis.__memorchessOAuthHref;
      globalThis.__memorchessOAuthHref = null;
      return href || null;
    }"""
  )

private fun uninstallMessageListener(): Unit =
  js(
    """{
      if (globalThis.__memorchessOAuthChannel) {
        globalThis.__memorchessOAuthChannel.close();
        globalThis.__memorchessOAuthChannel = null;
      }
      globalThis.__memorchessOAuthHref = null;
    }"""
  )

private fun decodeUriComponent(value: String): String = decodeUriComponentJs(value)

private fun decodeUriComponentJs(value: String): String = js("globalThis.decodeURIComponent(value)")
