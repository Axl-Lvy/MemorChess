package proj.memorchess.axl.core.data.online.auth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import co.touchlab.kermit.Logger
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.exception.AuthRestException
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
class AuthManager(private val supabaseClient: SupabaseClient) {

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
      try {
        supabaseClient.auth.refreshSession(refreshToken = refreshToken)
      } catch (e: AuthRestException) {
        KEEP_LOGGED_IN_SETTING.reset()
        LOGGER.e(e) { "Failed to restore session." }
        updateSavedTokens()
      }
    }
  }

  /** The authenticated user. This class ensures this state is always up to date. */
  var user by mutableStateOf(supabaseClient.auth.currentUserOrNull())
    private set

  /** Refresh [user] */
  private fun refreshUser() {
    val wasNull = user == null
    user = supabaseClient.auth.currentUserOrNull()
    if (user != null && wasNull) {
      LOGGER.i { "User signed in: ${user?.email}" }
    } else if (user == null && !wasNull) {
      LOGGER.i { "User signed out" }
    }
    updateSavedTokens()
  }

  fun updateSavedTokens() {
    val session = supabaseClient.auth.currentSessionOrNull()
    if (session != null && KEEP_LOGGED_IN_SETTING.getValue()) {
      AUTH_REFRESH_TOKEN_SETTINGS.setValue(session.refreshToken)
      AUTH_ACCESS_TOKEN_SETTINGS.setValue(session.accessToken)
    } else {
      KEEP_LOGGED_IN_SETTING.reset()
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
  suspend fun signInFromEmail(providedEmail: String, providedPassword: String) {
    supabaseClient.auth.signInWith(Email) {
      email = providedEmail
      password = providedPassword
    }
  }

  suspend fun signOut() {
    supabaseClient.auth.signOut()
  }

  fun registerListener(listener: suspend (SessionStatus) -> Unit) {
    authListeningScope.launch {
      supabaseClient.auth.sessionStatus.collect {
        refreshUser()
        listener(it)
      }
    }
  }

  fun isUserLoggedIn(): Boolean {
    return user != null
  }
}

private val LOGGER = Logger.withTag("AuthManager")
