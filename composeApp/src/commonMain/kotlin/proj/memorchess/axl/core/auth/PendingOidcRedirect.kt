package proj.memorchess.axl.core.auth

import com.russhwolf.settings.Settings

/** In-flight wasmJs redirect sign-in: PKCE verifier, CSRF state, and the hash to return to. */
internal data class PendingOidcRedirect(
  val state: String,
  val codeVerifier: String,
  val returnHash: String,
)

/**
 * Persists at most one [PendingOidcRedirect] across the wasmJs redirect page reload. A new [save]
 * overwrites any abandoned attempt, so two tabs signing in concurrently invalidate each other.
 */
internal class PendingOidcRedirectStore(private val settings: Settings) {

  fun save(record: PendingOidcRedirect) {
    settings.putString(KEY_STATE, record.state)
    settings.putString(KEY_VERIFIER, record.codeVerifier)
    settings.putString(KEY_RETURN_HASH, record.returnHash)
  }

  /** The stored record, or `null` if none is stored. */
  fun load(): PendingOidcRedirect? {
    val state = settings.getStringOrNull(KEY_STATE) ?: return null
    val verifier = settings.getStringOrNull(KEY_VERIFIER) ?: return null
    val returnHash = settings.getStringOrNull(KEY_RETURN_HASH) ?: ""
    return PendingOidcRedirect(state, verifier, returnHash)
  }

  fun clear() {
    settings.remove(KEY_STATE)
    settings.remove(KEY_VERIFIER)
    settings.remove(KEY_RETURN_HASH)
  }

  private companion object {
    const val KEY_STATE = "sync.oidc.pending.state"
    const val KEY_VERIFIER = "sync.oidc.pending.verifier"
    const val KEY_RETURN_HASH = "sync.oidc.pending.return_hash"
  }
}
