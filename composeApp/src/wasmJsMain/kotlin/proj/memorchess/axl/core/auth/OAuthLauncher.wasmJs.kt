@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package proj.memorchess.axl.core.auth

import kotlin.time.Duration.Companion.minutes
import kotlinx.browser.window
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Opens the authorization URL in a popup and awaits the callback page's `BroadcastChannel` message.
 * Chrome detaches the popup from its opener on lichess.org, so `popup.closed` and `window.opener`
 * cannot be trusted; a popup closed by hand is only noticed after [SIGN_IN_TIMEOUT].
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
 * Captures the callback href matching [redirectUri] and [expectedState]; the state filter ignores
 * leftovers from older concurrent attempts. The channel name must match the server's
 * `BROADCAST_CHANNEL_NAME` by hand.
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

/** Returns and clears the href captured by [installMessageListener], or `null` if none arrived. */
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
