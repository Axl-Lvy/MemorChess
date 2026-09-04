package proj.memorchess.axl.core.auth

import com.russhwolf.settings.Settings
import kotlin.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Persists the sync session (access token, refresh token, expiry and the signed in account).
 *
 * Backed by [Settings], same as [OAuthTokenStore]: unencrypted at rest, acceptable for a token
 * scoped to this app's own API rather than a third party's broader account.
 */
class OidcTokenStore(private val settings: Settings) {

  private val _currentAccount = MutableStateFlow(loadAccount())

  /** Currently signed in account, or `null` if no session is stored. */
  val currentAccount: StateFlow<Account?> = _currentAccount.asStateFlow()

  /** The access token, or `null` if there is no stored session. */
  fun getAccessToken(): String? = settings.getStringOrNull(KEY_ACCESS_TOKEN)

  /** The refresh token, or `null` if there is no stored session or none was ever issued. */
  fun getRefreshToken(): String? = settings.getStringOrNull(KEY_REFRESH_TOKEN)

  /** When [getAccessToken] expires, or `null` if there is no stored session. */
  fun getExpiresAt(): Instant? =
    settings.getLongOrNull(KEY_EXPIRES_AT)?.let(Instant::fromEpochMilliseconds)

  /**
   * Persists a token set. A `null` [refreshToken] clears any previously stored one (the issuer
   * did not renew it, so the old value should not be treated as still valid). A `null` [account]
   * leaves the previously known account untouched, since a refresh response typically carries no
   * id token.
   */
  fun save(accessToken: String, refreshToken: String?, expiresAt: Instant, account: Account?) {
    settings.putString(KEY_ACCESS_TOKEN, accessToken)
    if (refreshToken != null) {
      settings.putString(KEY_REFRESH_TOKEN, refreshToken)
    } else {
      settings.remove(KEY_REFRESH_TOKEN)
    }
    settings.putLong(KEY_EXPIRES_AT, expiresAt.toEpochMilliseconds())
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
