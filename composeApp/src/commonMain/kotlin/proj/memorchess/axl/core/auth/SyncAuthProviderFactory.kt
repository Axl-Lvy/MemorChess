package proj.memorchess.axl.core.auth

/**
 * Builds this platform's [AuthProvider] for the sync identity flow. Native platforms sign in
 * through a popup via [launcher]; wasmJs uses a full page redirect.
 */
internal expect fun getPlatformSpecificSyncAuthProvider(
  oidcClient: OidcClient,
  tokenStore: OidcTokenStore,
  launcher: OAuthLauncher,
  redirectUri: String,
  clientId: String,
): AuthProvider
