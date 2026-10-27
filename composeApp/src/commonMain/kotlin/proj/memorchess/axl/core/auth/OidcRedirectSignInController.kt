package proj.memorchess.axl.core.auth

import kotlinx.coroutines.awaitCancellation

/**
 * wasmJs [AuthProvider] that signs in via a full page redirect, since Google's
 * `Cross-Origin-Opener-Policy: same-origin` breaks the popup flow [delegate] uses.
 */
internal class OidcRedirectSignInController(
  private val delegate: AuthProvider,
  private val oidcClient: OidcClient,
  private val pendingStore: PendingOidcRedirectStore,
  private val redirectUri: String,
  private val clientId: String,
  private val audience: String,
  private val navigate: (String) -> Unit,
  private val currentHash: () -> String,
) : AuthProvider by delegate {

  /** Persists PKCE state and navigates away; never returns since the page unloads. */
  override suspend fun signIn(): SignInResult {
    val pkce = PkceGenerator.generate()
    val state = generateOidcState()
    pendingStore.save(PendingOidcRedirect(state, pkce.verifier, currentHash()))
    val authorizationUrl =
      oidcClient.buildAuthorizationUrl(
        clientId = clientId,
        redirectUri = redirectUri,
        codeChallenge = pkce.challenge,
        state = state,
        audience = audience,
      )
    navigate(authorizationUrl)
    awaitCancellation()
  }
}
