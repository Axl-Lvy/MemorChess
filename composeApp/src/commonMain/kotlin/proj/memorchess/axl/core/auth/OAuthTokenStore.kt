package proj.memorchess.axl.core.auth

import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Persists the Lichess OAuth access token and the username it belongs to. Storage is not encrypted
 * at rest, which is acceptable for a read only token without scopes.
 */
class OAuthTokenStore(private val settings: Settings) {

  private val _account = MutableStateFlow(load())

  /** Currently signed in account, or `null` if no token is stored. */
  val account: StateFlow<LichessAccount?> = _account.asStateFlow()

  /** Returns the access token, or `null` if the user is not signed in. */
  fun getToken(): LichessToken? = settings.getStringOrNull(KEY_TOKEN)?.let(::LichessToken)

  /** Persists [token] and [username]; a `null` [username] means it is not resolved yet. */
  fun save(token: LichessToken, username: String?) {
    settings.putString(KEY_TOKEN, token.value)
    if (username != null) {
      settings.putString(KEY_USERNAME, username)
    } else {
      settings.remove(KEY_USERNAME)
    }
    _account.value = LichessAccount(token = token, username = username)
  }

  /** Persists [username] without changing the token; does nothing when signed out. */
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
    val token = getToken() ?: return null
    val username = settings.getStringOrNull(KEY_USERNAME)
    return LichessAccount(token = token, username = username)
  }

  private companion object {
    const val KEY_TOKEN = "lichess.oauth.token"
    const val KEY_USERNAME = "lichess.oauth.username"
  }
}

/** Signed in Lichess account. */
data class LichessAccount(val token: LichessToken, val username: String?)
