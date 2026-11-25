package proj.memorchess.axl.dev

import kotlin.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import proj.memorchess.axl.core.auth.Account
import proj.memorchess.axl.core.auth.AuthProvider
import proj.memorchess.axl.core.auth.SignInResult
import proj.memorchess.axl.core.auth.TokenResult
import proj.memorchess.axl.core.date.DateUtil

/**
 * Signs a dev launch in as the local dev server's fake user, straight from [session]. It never runs
 * the real sign-in flow, so real-account credentials can never land in dev settings.
 */
internal class DevAuthProvider(
  private val session: DevSession?,
  private val clock: () -> Instant = DateUtil::now,
) : AuthProvider {

  private val _currentAccount = MutableStateFlow(validSession()?.account)

  override val currentAccount: StateFlow<Account?> = _currentAccount.asStateFlow()

  override suspend fun signIn(): SignInResult {
    val valid = validSession() ?: return SignInResult.Cancelled
    _currentAccount.value = valid.account
    return SignInResult.Success
  }

  override fun signOut() {
    _currentAccount.value = null
  }

  override suspend fun accessToken(): TokenResult {
    if (_currentAccount.value == null) return TokenResult.SignedOut
    val valid = validSession()
    if (valid == null) {
      System.err.println("Dev session expired: restart ./gradlew :server:devRun")
      _currentAccount.value = null
      return TokenResult.SignedOut
    }
    return TokenResult.Ok(valid.accessToken)
  }

  private fun validSession(): DevSession? = session?.takeIf { clock() < it.expiresAt }
}
