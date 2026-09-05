package proj.memorchess.axl.core.auth

internal actual fun getPlatformSpecificSyncAuthProvider(
  oidcClient: OidcClient,
  tokenStore: OidcTokenStore,
  launcher: OAuthLauncher,
  redirectUri: String,
  clientId: String,
): AuthProvider =
  OidcSignInController(
    launcher = launcher,
    oidcClient = oidcClient,
    tokenStore = tokenStore,
    redirectUri = redirectUri,
    clientId = clientId,
  )
