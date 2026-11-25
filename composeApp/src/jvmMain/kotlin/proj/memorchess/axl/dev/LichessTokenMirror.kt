package proj.memorchess.axl.dev

import com.russhwolf.settings.PreferencesSettings
import java.util.prefs.Preferences
import proj.memorchess.axl.core.auth.OAuthTokenStore

/**
 * Returns the Lichess token store of the normal desktop settings, or `null` when that node does not
 * exist. Checking first matters: `Preferences.node` would create it.
 */
internal fun sharedLichessTokenStore(nodePath: String = SHARED_PREFERENCES_NODE): OAuthTokenStore? {
  val root = Preferences.userRoot()
  if (!root.nodeExists(nodePath)) return null
  return OAuthTokenStore(PreferencesSettings(root.node(nodePath)))
}

/** Copies the Lichess account of [source] into [target]; nothing else is read or written. */
internal fun mirrorLichessToken(source: OAuthTokenStore?, target: OAuthTokenStore) {
  val account = source?.account?.value ?: return
  target.save(account.token, account.username)
}
