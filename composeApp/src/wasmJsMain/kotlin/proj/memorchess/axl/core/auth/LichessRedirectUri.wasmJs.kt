package proj.memorchess.axl.core.auth

import kotlinx.browser.window

/** Built from the page origin, so the parent can read the popup's `location.href` on return. */
actual val LICHESS_REDIRECT_URI: String
  get() = "${window.location.origin}/oauth-callback"
