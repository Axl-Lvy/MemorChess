package proj.memorchess.axl.core.data.online.auth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.diamondedge.logging.logging
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import proj.memorchess.axl.core.config.AUTH_ACCESS_TOKEN_SETTINGS
import proj.memorchess.axl.core.config.AUTH_REFRESH_TOKEN_SETTINGS
import proj.memorchess.axl.core.config.KEEP_LOGGED_IN_SETTING

/**
 * A singleton class for managing Supabase authentication
 *
 * @property supabaseClient
 * @constructor Create empty Supabase auth manager
 */
class BasicAuthManager(private val supabaseClient: SupabaseClient) : AuthManager {

  private val authListeningScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

  init {
    authListeningScope.launch {
      supabaseClient.auth.sessionStatus.collect {
        when (it) {
          is SessionStatus.Authenticated -> {
            refreshUser()
          }
          SessionStatus.Initializing -> LOGGER.i { "Initializing" }
          is SessionStatus.RefreshFailure -> {
            LOGGER.w { "Session expired and could not be refreshed" }
          }
          is SessionStatus.NotAuthenticated -> {
            if (it.isSignOut) {
              refreshUser()
            } else {
              LOGGER.i { "User not signed in" }
            }
          }
        }
      }
    }
    if (KEEP_LOGGED_IN_SETTING.getValue()) {
      authListeningScope.launch { tryRestoreSession() }
    }
  }

  private suspend fun tryRestoreSession() {
    val refreshToken = AUTH_REFRESH_TOKEN_SETTINGS.getValue()
    if (refreshToken.isNotEmpty()) {
      supabaseClient.auth.refreshSession(refreshToken = refreshToken)
    }
  }

  /** The authenticated user. This class ensures this state is always up to date. */
  override var user by mutableStateOf(supabaseClient.auth.currentUserOrNull())
    private set

  /** Refresh [user] */
  private fun refreshUser() {
    user = supabaseClient.auth.currentUserOrNull()
    val session = supabaseClient.auth.currentSessionOrNull()
    if (session != null) {
      AUTH_REFRESH_TOKEN_SETTINGS.setValue(session.refreshToken)
      AUTH_ACCESS_TOKEN_SETTINGS.setValue(session.accessToken)
    } else {
      AUTH_REFRESH_TOKEN_SETTINGS.reset()
      AUTH_ACCESS_TOKEN_SETTINGS.reset()
    }
  }

  /**
   * Sign in from email.
   *
   * @param providedEmail email
   * @param providedPassword password
   */
  override suspend fun signInFromEmail(providedEmail: String, providedPassword: String) {
    supabaseClient.auth.signInWith(Email) {
      email = providedEmail
      password = providedPassword
    }
  }

  override suspend fun signUpFromEmail(providedEmail: String, providedPassword: String) {
    supabaseClient.auth.signUpWith(Email) {
      email = providedEmail
      password = providedPassword
    }
  }

  override suspend fun confirmEmail(email: String, token: String) {
    supabaseClient.auth.verifyEmailOtp(OtpType.Email.MAGIC_LINK, token)
  }

  override suspend fun signOut() {
    supabaseClient.auth.signOut()
  }
}

private val LOGGER = logging()
