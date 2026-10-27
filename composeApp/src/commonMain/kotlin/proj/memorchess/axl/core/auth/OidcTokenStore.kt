package proj.memorchess.axl.core.auth

import com.russhwolf.settings.Settings
import kotlin.time.Instant
import proj.memorchess.axl.core.config.getInstantOrNull
import proj.memorchess.axl.core.config.putInstant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Persists the sync session: tokens, expiry and the signed in account. Unencrypted at rest, which
 * is acceptable for a token scoped to this app's own API.
 */
class OidcTokenStore(private val settings: Settings) {

  private val _currentAccount = MutableStateFlow(loadAccount())

  /** Currently signed in account, or `null` if no session is stored. */
  val currentAccount: StateFlow<Account?> = _currentAccount.asStateFlow()

  /** The access token, or `null` if there is no stored session. */
  fun getAccessToken(): AccessToken? = settings.getStringOrNull(KEY_ACCESS_TOKEN)?.let(::AccessToken)

  /** The refresh token, or `null` if there is no stored session or none was ever issued. */
  fun getRefreshToken(): RefreshToken? =
    settings.getStringOrNull(KEY_REFRESH_TOKEN)?.let(::RefreshToken)

  /** When [getAccessToken] expires, or `null` if there is no stored session. */
  fun getExpiresAt(): Instant? =
    settings.getInstantOrNull(KEY_EXPIRES_AT)

  /**
   * Persists a token set. A `null` [refreshToken] clears the stored one; a `null` [account] keeps
   * the previously known account.
   */
  fun save(
    accessToken: AccessToken,
    refreshToken: RefreshToken?,
    expiresAt: Instant,
    account: Account?,
  ) {
    settings.putString(KEY_ACCESS_TOKEN, accessToken.value)
    if (refreshToken != null) {
      settings.putString(KEY_REFRESH_TOKEN, refreshToken.value)
    } else {
      settings.remove(KEY_REFRESH_TOKEN)
    }
    settings.putInstant(KEY_EXPIRES_AT, expiresAt)
    if (account != null) {
      settings.putString(KEY_SUB, account.sub)
      if (account.name != null) {
        settings.putString(KEY_NAME, account.name)
      } else {
        settings.remove(KEY_NAME)
      }
      _currentAccount.value = account
    }
  }

  /** Clears the stored session entirely. */
  fun clear() {
    settings.remove(KEY_ACCESS_TOKEN)
    settings.remove(KEY_REFRESH_TOKEN)
    settings.remove(KEY_EXPIRES_AT)
    settings.remove(KEY_SUB)
    settings.remove(KEY_NAME)
    _currentAccount.value = null
  }

  private fun loadAccount(): Account? {
    val sub = settings.getStringOrNull(KEY_SUB) ?: return null
    return Account(sub = sub, name = settings.getStringOrNull(KEY_NAME))
  }

  private companion object {
    const val KEY_ACCESS_TOKEN = "sync.oidc.access_token"
    const val KEY_REFRESH_TOKEN = "sync.oidc.refresh_token"
    const val KEY_EXPIRES_AT = "sync.oidc.expires_at"
    const val KEY_SUB = "sync.oidc.sub"
    const val KEY_NAME = "sync.oidc.name"
  }
}
