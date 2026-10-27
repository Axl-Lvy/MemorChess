package proj.memorchess.axl.core.auth

import kotlin.coroutines.resume
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.AuthenticationServices.ASWebAuthenticationSession
import platform.Foundation.NSURL
import platform.Foundation.NSURLComponents
import platform.Foundation.NSURLQueryItem

/**
 * iOS OAuth launcher backed by [ASWebAuthenticationSession]. The redirect URI must use a custom
 * scheme (e.g. `memorchess://oauth`) so iOS intercepts it without an Info.plist entry.
 */
actual class OAuthLauncher {

  @OptIn(ExperimentalForeignApi::class)
  actual suspend fun launch(
    authorizationUrl: String,
    redirectUri: String,
    expectedState: String,
  ): OAuthLaunchResult = suspendCancellableCoroutine { cont ->
    val callbackScheme = redirectUri.substringBefore("://")
    val url = NSURL(string = authorizationUrl)
    val session =
      ASWebAuthenticationSession(uRL = url, callbackURLScheme = callbackScheme) { callbackUrl, error
        ->
        if (cont.isActive.not()) return@ASWebAuthenticationSession
        val result =
          when {
            error != null -> OAuthLaunchResult.Cancelled
            callbackUrl == null -> OAuthLaunchResult.Error(OAuthLaunchError.MISSING_CODE)
            else -> parseCallback(callbackUrl, expectedState)
          }
        cont.resume(result)
      }
    if (!session.start()) {
      if (cont.isActive) {
        cont.resume(OAuthLaunchResult.Error(OAuthLaunchError.PLATFORM_ERROR))
      }
    }
    cont.invokeOnCancellation { session.cancel() }
  }

  @OptIn(ExperimentalForeignApi::class)
  private fun parseCallback(url: NSURL, expectedState: String): OAuthLaunchResult {
    val components = NSURLComponents.componentsWithURL(url, resolvingAgainstBaseURL = false)
    val items = components?.queryItems?.filterIsInstance<NSURLQueryItem>().orEmpty()
    val params = items.associate { it.name to (it.value ?: "") }
    val code = params["code"]
    val state = params["state"]
    return when {
      code == null -> OAuthLaunchResult.Error(OAuthLaunchError.MISSING_CODE)
      state != expectedState -> OAuthLaunchResult.Error(OAuthLaunchError.STATE_MISMATCH)
      else -> OAuthLaunchResult.Ok(code)
    }
  }
}
