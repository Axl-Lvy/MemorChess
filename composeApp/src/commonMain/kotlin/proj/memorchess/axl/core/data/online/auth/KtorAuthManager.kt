package proj.memorchess.axl.core.data.online.auth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import co.touchlab.kermit.Logger
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.resources.get
import io.ktor.client.request.forms.submitForm
import io.ktor.http.parameters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import proj.memorchess.axl.core.config.AUTH_ACCESS_TOKEN_SETTINGS
import proj.memorchess.axl.core.config.AUTH_REFRESH_TOKEN_SETTINGS
import proj.memorchess.axl.core.config.KEEP_LOGGED_IN_SETTING
import proj.memorchess.axl.core.data.book.UserPermission
import proj.memorchess.axl.core.data.online.BASE_URL
import proj.memorchess.axl.core.data.online.CredentialsHolder
import proj.memorchess.axl.shared.routes.UserRoutes

/**
 * Manages authentication using Ktor client to communicate with the custom server.
 *
 * @property httpClient The Ktor HTTP client for making requests
 */
class KtorAuthManager(private val httpClient: HttpClient) {

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
  private val listeners = mutableListOf<suspend (AuthEvent) -> Unit>()

  init {
    if (KEEP_LOGGED_IN_SETTING.getValue()) {
      tryRestoreSession()
    }
  }

  private fun tryRestoreSession() {
    val username = AUTH_ACCESS_TOKEN_SETTINGS.getValue()
    val password = AUTH_REFRESH_TOKEN_SETTINGS.getValue()
    if (username.isNotEmpty() && password.isNotEmpty()) {
      try {
        setCredentials(username, password)
        user = User(email = username, password = password)
        LOGGER.i { "Session restored for user: $username" }
        notifyListeners(AuthEvent.Authenticated)
      } catch (e: Exception) {
        KEEP_LOGGED_IN_SETTING.reset()
        LOGGER.e(e) { "Failed to restore session." }
        updateSavedTokens()
      }
    }
  }

  /** Updates the shared [CredentialsHolder] used by the HTTP client's auth plugin. */
  private fun setCredentials(username: String, password: String) {
    CredentialsHolder.username = username
    CredentialsHolder.password = password
  }

  /** The authenticated user. This class ensures this state is always up to date. */
  var user by mutableStateOf<User?>(null)
    private set

  /** Refresh [user] state */
  private fun refreshUser(newUser: User?) {
    val wasNull = user == null
    user = newUser
    if (user != null && wasNull) {
      LOGGER.i { "User signed in: ${user?.email}" }
    } else if (user == null && !wasNull) {
      LOGGER.i { "User signed out" }
    }
    permissionsCache.clear()
    updateSavedTokens()
  }

  fun updateSavedTokens() {
    if (user != null && KEEP_LOGGED_IN_SETTING.getValue()) {
      AUTH_ACCESS_TOKEN_SETTINGS.setValue(user?.email ?: "")
      AUTH_REFRESH_TOKEN_SETTINGS.setValue(user?.password ?: "")
    } else {
      KEEP_LOGGED_IN_SETTING.reset()
      AUTH_REFRESH_TOKEN_SETTINGS.reset()
      AUTH_ACCESS_TOKEN_SETTINGS.reset()
    }
  }

  /**
   * Sign in from email.
   *
   * @param providedEmail email (used as username)
   * @param providedPassword password
   */
  suspend fun signInFromEmail(providedEmail: String, providedPassword: String) {
    try {
      setCredentials(providedEmail, providedPassword)

      // Test the authentication by making a login request
      httpClient.submitForm(
        url = "$BASE_URL/login",
        formParameters =
          parameters {
            append("username", providedEmail)
            append("password", providedPassword)
          },
      )

      // If successful, update user state
      refreshUser(User(email = providedEmail, password = providedPassword))
      notifyListeners(AuthEvent.Authenticated)
      LOGGER.i { "Successfully signed in: $providedEmail" }
    } catch (e: Exception) {
      setCredentials("", "")
      LOGGER.e(e) { "Failed to sign in" }
      throw e
    }
  }

  suspend fun signOut() {
    refreshUser(null)
    setCredentials("", "")
    notifyListeners(AuthEvent.SignedOut)
    LOGGER.i { "User signed out" }
  }

  /** Registers a listener that is called on authentication events. */
  fun registerListener(listener: suspend (AuthEvent) -> Unit) {
    listeners.add(listener)
  }

  private fun notifyListeners(event: AuthEvent) {
    for (listener in listeners) {
      scope.launch { listener(event) }
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
        httpClient.get(UserRoutes.Permission(permission = permission.value)).body<Boolean>()
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

private val LOGGER = Logger.withTag("KtorAuthManager")

/** Simple user data class to represent an authenticated user */
data class User(val email: String, val password: String)

/** Authentication events emitted by [KtorAuthManager]. */
sealed interface AuthEvent {
  data object Authenticated : AuthEvent
  data object SignedOut : AuthEvent
}
