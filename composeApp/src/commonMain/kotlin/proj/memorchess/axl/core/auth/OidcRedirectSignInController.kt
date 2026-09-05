package proj.memorchess.axl.core.auth

import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.StateFlow

/**
 * wasmJs [AuthProvider]: initiates sign in via a full page redirect instead of the popup [delegate]
 * would otherwise use, because Google's `accounts.google.com` sends
 * `Cross-Origin-Opener-Policy: same-origin`, which permanently breaks the popup + polling technique
 * for the life of that popup (see the design spec). Delegates [signOut], [accessToken], and
 * [currentAccount] unchanged, since that logic never touches the popup.
 *
 * [navigate] performs the actual page navigation (real wiring: `window.location.href = it`);
 * [currentHash] reads the page's current hash so the user returns to it after sign in.
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
) : AuthProvider {

  override val currentAccount: StateFlow<Account?> = delegate.currentAccount

  /**
   * Persists PKCE state and navigates away; never returns a [SignInResult] because the page
   * unloads. The resulting sign in completes separately, on the next app start (see
   * `exchangeOidcRedirectCode`).
   */
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

  override fun signOut() = delegate.signOut()

  override suspend fun accessToken(): TokenResult = delegate.accessToken()
}
