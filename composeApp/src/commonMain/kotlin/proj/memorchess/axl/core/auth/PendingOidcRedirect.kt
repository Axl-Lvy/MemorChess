package proj.memorchess.axl.core.auth

import com.russhwolf.settings.Settings

/**
 * One in-flight wasmJs redirect sign-in attempt: the PKCE verifier and CSRF state generated before
 * navigating to the authorization URL, plus the hash to return to once it completes.
 */
internal data class PendingOidcRedirect(
  val state: String,
  val codeVerifier: String,
  val returnHash: String,
)

/**
 * Persists at most one [PendingOidcRedirect] across the full page reload the wasmJs redirect flow
 * requires. Backed by [Settings], same store [OidcTokenStore] uses. A new [save] overwrites any
 * earlier abandoned attempt, so no explicit expiry is needed; two tabs starting sign in
 * concurrently share this one record and the first to finish invalidates the other, an accepted
 * limitation for this app's traffic (see the design spec).
 */
internal class PendingOidcRedirectStore(private val settings: Settings) {

  /** Overwrites any previously stored record. */
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

  /** Removes the stored record, if any. */
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
