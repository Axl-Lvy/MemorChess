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
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import proj.memorchess.axl.core.config.AUTH_ACCESS_TOKEN_SETTINGS
import proj.memorchess.axl.core.config.AUTH_REFRESH_TOKEN_SETTINGS
import proj.memorchess.axl.core.config.FeatureFlags
import proj.memorchess.axl.core.config.KEEP_LOGGED_IN_SETTING
import proj.memorchess.axl.core.data.book.UserPermission

/**
 * A singleton class for managing Supabase authentication
 *
 * @property supabaseClient
 * @constructor Create empty Supabase auth manager
 */
class AuthManager(private val supabaseClient: SupabaseClient) {

  private val authListeningScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

  init {
    if (FeatureFlags.isAuthEnabled) {
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
    permissionsCache.clear()
    updateSavedTokens()
  }

  /** Persists or clears the session tokens based on the current session and keep-logged-in setting. */
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

  /** Signs the current user out. */
  suspend fun signOut() {
    supabaseClient.auth.signOut()
  }

  /** Registers a [listener] that is invoked on every [SessionStatus] change. */
  fun registerListener(listener: suspend (SessionStatus) -> Unit) {
    authListeningScope.launch {
      supabaseClient.auth.sessionStatus.collect {
        refreshUser()
        listener(it)
      }
    }
  }

  private val permissionsCache = mutableMapOf<UserPermission, Boolean>()

  /** Checks if the current user has the specified permission. */
  suspend fun hasUserPermission(permission: UserPermission): Boolean {
    if (!isUserLoggedIn()) {
      return false
    }
    permissionsCache[permission]?.let {
      return it
    }
    try {
      val result =
        supabaseClient.postgrest
          .rpc("check_user_permission", CheckPermissionFunctionArg(permission.value))
          .decodeAs<Boolean>()
      permissionsCache[permission] = result
      return result
    } catch (e: Exception) {
      LOGGER.e(e) { "Failed to check user permission: ${permission.value}" }
      return false
    }
  }

  /** Returns true if a user is currently logged in. */
  fun isUserLoggedIn(): Boolean {
    return user != null
  }
}

private val LOGGER = Logger.withTag("AuthManager")

@Serializable
private data class CheckPermissionFunctionArg(
  @SerialName("permission_input") val permission: String
)
