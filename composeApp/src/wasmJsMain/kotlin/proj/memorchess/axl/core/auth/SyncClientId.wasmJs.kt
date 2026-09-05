package proj.memorchess.axl.core.auth

import kotlinx.browser.window

actual val SYNC_CLIENT_ID: String = "9o2yn87g7bwvpewsqijxe"

actual val SYNC_REDIRECT_URI: String
  get() = "${window.location.origin}$SYNC_REDIRECT_PATH"
