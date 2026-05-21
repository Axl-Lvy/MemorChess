@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package proj.memorchess.axl.core.auth

import kotlin.time.Duration.Companion.minutes
import kotlinx.browser.window
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Web OAuth launcher.
 *
 * Opens [authorizationUrl] in a popup window and polls the popup's URL until it lands on
 * [redirectUri]. Cross origin reads while the popup is on `lichess.org` throw and are swallowed
 * silently; once the user authorizes and the browser navigates the popup back to our origin (the
 * value of `redirectUri`), the URL becomes readable and we extract the code.
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
    val popup =
      window.open(authorizationUrl, "memorchess_oauth", "popup=yes,width=600,height=700")
        ?: return OAuthLaunchResult.Error(OAuthLaunchError.BROWSER_UNAVAILABLE)
    val result =
      withTimeoutOrNull(SIGN_IN_TIMEOUT) {
        while (true) {
          if (popup.closed) return@withTimeoutOrNull OAuthLaunchResult.Cancelled
          val href = readPopupHref(popup)
          if (href != null && href.startsWith(redirectUri)) {
            return@withTimeoutOrNull parseRedirect(href, expectedState)
          }
          delay(POLL_INTERVAL_MS)
        }
        @Suppress("UNREACHABLE_CODE") OAuthLaunchResult.Error(OAuthLaunchError.PLATFORM_ERROR)
      } ?: OAuthLaunchResult.Error(OAuthLaunchError.PLATFORM_ERROR)
    if (!popup.closed) popup.close()
    return result
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
 * Reads the popup's URL while tolerating cross origin errors thrown while the popup is still on
 * lichess.org. Returns `null` on any failure.
 */
private fun readPopupHref(popup: org.w3c.dom.Window): String? {
  return try {
    readHrefJs(popup)
  } catch (e: Throwable) {
    null
  }
}

private fun readHrefJs(popup: org.w3c.dom.Window): String? = js("popup.location.href")

private fun decodeUriComponent(value: String): String = decodeUriComponentJs(value)

private fun decodeUriComponentJs(value: String): String = js("globalThis.decodeURIComponent(value)")
