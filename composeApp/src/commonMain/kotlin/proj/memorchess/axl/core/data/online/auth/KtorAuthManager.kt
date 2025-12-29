package proj.memorchess.axl.core.data.online.auth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import co.touchlab.kermit.Logger
import io.ktor.client.HttpClient
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BasicAuthCredentials
import io.ktor.client.plugins.auth.providers.basic
import io.ktor.client.request.forms.submitForm
import io.ktor.http.parameters
import proj.memorchess.axl.core.config.AUTH_ACCESS_TOKEN_SETTINGS
import proj.memorchess.axl.core.config.AUTH_REFRESH_TOKEN_SETTINGS
import proj.memorchess.axl.core.config.KEEP_LOGGED_IN_SETTING
import proj.memorchess.axl.core.data.book.UserPermission
import proj.memorchess.axl.core.data.online.BASE_URL

/**
 * Manages authentication using Ktor client to communicate with the custom server.
 *
 * This replaces Supabase authentication with a custom Ktor-based implementation. It mirrors the
 * public API of [AuthManager] to allow for a drop-in replacement.
 *
 * @property httpClient The Ktor HTTP client for making requests
 */
class KtorAuthManager(private val httpClient: HttpClient) {

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
        configureAuth(username, password)
        user = User(email = username, password = password)
        LOGGER.i { "Session restored for user: $username" }
      } catch (e: Exception) {
        KEEP_LOGGED_IN_SETTING.reset()
        LOGGER.e(e) { "Failed to restore session." }
        updateSavedTokens()
      }
    }
  }

  /**
   * Configures the HTTP client's auth plugin with the provided credentials.
   *
   * This uses Ktor's built-in basic authentication provider.
   */
  private fun configureAuth(username: String, password: String) {
    httpClient.config {
      install(Auth) {
        basic {
          credentials { BasicAuthCredentials(username = username, password = password) }
          sendWithoutRequest { request -> request.url.host.contains("localhost") }
        }
      }
    }
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
      // Configure auth plugin with credentials
      configureAuth(providedEmail, providedPassword)

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
      LOGGER.i { "Successfully signed in: $providedEmail" }
    } catch (e: Exception) {
      LOGGER.e(e) { "Failed to sign in" }
      throw e
    }
  }

  suspend fun signOut() {
    refreshUser(null)
    // Clear auth configuration
    httpClient.config {
      install(Auth) { basic { credentials { BasicAuthCredentials(username = "", password = "") } } }
    }
    LOGGER.i { "User signed out" }
  }

  private val permissionsCache = mutableMapOf<UserPermission, Boolean>()

  /**
   * Checks if the current user has the specified permission.
   *
   * TODO: Implement actual permission check with server endpoint
   */
  suspend fun hasUserPermission(permission: UserPermission): Boolean {
    if (!isUserLoggedIn()) {
      return false
    }
    permissionsCache[permission]?.let {
      return it
    }
    try {
      // TODO: Implement actual permission check with server
      // This should call an endpoint like GET /user/permissions?permission=BOOK_CREATION
      val result = false
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
