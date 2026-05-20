package proj.memorchess.axl.core.auth

import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Persists the Lichess OAuth access token and (optionally) the username it belongs to.
 *
 * Backed by [Settings] so the token survives process restarts on every platform. On Android this
 * lands in SharedPreferences, on iOS in NSUserDefaults, on JVM in a Properties file under the user
 * config directory, and on wasmJs in localStorage. None of those are encrypted at rest, which is
 * acceptable for a read only Lichess token without scopes.
 *
 * Token mutations push to [account] so the UI can recompose.
 */
class OAuthTokenStore(private val settings: Settings) {

  private val _account = MutableStateFlow(load())

  /** Currently signed in account, or `null` if no token is stored. */
  val account: StateFlow<LichessAccount?> = _account.asStateFlow()

  /** Returns the access token, or `null` if the user is not signed in. */
  fun getToken(): String? = settings.getStringOrNull(KEY_TOKEN)

  /** Persists [token] and [username]. Pass a `null` [username] if it has not been resolved yet. */
  fun save(token: String, username: String?) {
    settings.putString(KEY_TOKEN, token)
    if (username != null) {
      settings.putString(KEY_USERNAME, username)
    } else {
      settings.remove(KEY_USERNAME)
    }
    _account.value = LichessAccount(token = token, username = username)
  }

  /** Persists [username] without changing the token. Used after the initial token exchange. */
  fun setUsername(username: String) {
    val token = getToken() ?: return
    settings.putString(KEY_USERNAME, username)
    _account.value = LichessAccount(token = token, username = username)
  }

  /** Clears the token and username from storage. */
  fun clear() {
    settings.remove(KEY_TOKEN)
    settings.remove(KEY_USERNAME)
    _account.value = null
  }

  private fun load(): LichessAccount? {
    val token = settings.getStringOrNull(KEY_TOKEN) ?: return null
    val username = settings.getStringOrNull(KEY_USERNAME)
    return LichessAccount(token = token, username = username)
  }

  private companion object {
    const val KEY_TOKEN = "lichess.oauth.token"
    const val KEY_USERNAME = "lichess.oauth.username"
  }
}

/** Signed in Lichess account. */
data class LichessAccount(val token: String, val username: String?)
