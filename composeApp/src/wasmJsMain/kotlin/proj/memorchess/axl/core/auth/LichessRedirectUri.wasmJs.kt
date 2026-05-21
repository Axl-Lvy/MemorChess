package proj.memorchess.axl.core.auth

import kotlinx.browser.window

/**
 * Web redirect URI is built from the current page origin so the popup window navigates back to a
 * URL with the same origin (allowing the parent to read `location.href`).
 */
actual val LICHESS_REDIRECT_URI: String
  get() = "${window.location.origin}/oauth-callback"
