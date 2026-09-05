package proj.memorchess.axl.core.auth

import kotlinx.browser.window
import proj.memorchess.axl.core.config.getPlatformSpecificSettings

// `launcher` is unused here (wasmJs never launches a popup) but kept to match the `expect fun`
// signature shared with the nonJs actual, which does use it.
@Suppress("UNUSED_PARAMETER")
internal actual fun getPlatformSpecificSyncAuthProvider(
  oidcClient: OidcClient,
  tokenStore: OidcTokenStore,
  launcher: OAuthLauncher,
  redirectUri: String,
  clientId: String,
): AuthProvider =
  OidcRedirectSignInController(
    delegate =
      OidcSignInController(
        launch = { _, _, _ -> error("popup launch must never be called on wasmJs") },
        oidcClient = oidcClient,
        tokenStore = tokenStore,
        redirectUri = redirectUri,
        clientId = clientId,
      ),
    oidcClient = oidcClient,
    pendingStore = PendingOidcRedirectStore(getPlatformSpecificSettings()),
    redirectUri = redirectUri,
    clientId = clientId,
    audience = SYNC_AUDIENCE,
    navigate = { window.location.href = it },
    currentHash = { window.location.hash },
  )
