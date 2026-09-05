package proj.memorchess.axl.core.auth

/**
 * Builds this platform's [AuthProvider] for the sync/Logto identity flow. Native platforms
 * (Android, iOS, JVM) sign in through a popup via [launcher]; wasmJs uses a full page redirect
 * instead (see the design spec for why the popup technique is not viable there).
 */
internal expect fun getPlatformSpecificSyncAuthProvider(
  oidcClient: OidcClient,
  tokenStore: OidcTokenStore,
  launcher: OAuthLauncher,
  redirectUri: String,
  clientId: String,
): AuthProvider
