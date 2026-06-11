package proj.memorchess.axl.core.auth

import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import kotlinx.coroutines.CompletableDeferred
import proj.memorchess.axl.AndroidContextProvider

/**
 * Android OAuth launcher.
 *
 * Opens [authorizationUrl] in a Chrome Custom Tab. When Lichess redirects back to
 * `memorchess://oauth?...`, [LichessOAuthRedirectActivity] catches the redirect and forwards the
 * URI through [PendingOAuth].
 */
actual class OAuthLauncher {

  actual suspend fun launch(
    authorizationUrl: String,
    redirectUri: String,
    expectedState: String,
  ): OAuthLaunchResult {
    val deferred = CompletableDeferred<OAuthLaunchResult>()
    PendingOAuth.start(expectedState, deferred)
    return try {
      try {
        val intent =
          CustomTabsIntent.Builder().build().apply {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
          }
        intent.launchUrl(AndroidContextProvider.context, Uri.parse(authorizationUrl))
      } catch (e: Exception) {
        deferred.complete(OAuthLaunchResult.Error(OAuthLaunchError.BROWSER_UNAVAILABLE))
      }
      deferred.await()
    } finally {
      PendingOAuth.clear()
    }
  }
}

/**
 * Holds the in flight OAuth deferred so [LichessOAuthRedirectActivity] can complete it when the
 * browser bounces back.
 */
internal object PendingOAuth {
  private var expectedState: String? = null
  private var deferred: CompletableDeferred<OAuthLaunchResult>? = null

  fun start(state: String, target: CompletableDeferred<OAuthLaunchResult>) {
    expectedState = state
    deferred = target
  }

  fun complete(uri: Uri) {
    val target = deferred ?: return
    val expected = expectedState
    val state = uri.getQueryParameter("state")
    val code = uri.getQueryParameter("code")
    val result =
      when {
        code == null -> OAuthLaunchResult.Error(OAuthLaunchError.MISSING_CODE)
        expected != null && state != expected ->
          OAuthLaunchResult.Error(OAuthLaunchError.STATE_MISMATCH)
        else -> OAuthLaunchResult.Ok(code)
      }
    target.complete(result)
    clear()
  }

  fun cancel() {
    deferred?.complete(OAuthLaunchResult.Cancelled)
    clear()
  }

  fun clear() {
    expectedState = null
    deferred = null
  }
}
