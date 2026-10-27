package proj.memorchess.axl.core.sync

import kotlinx.browser.window

/** The web build shares its origin with the sync API, so that origin is the base URL. */
actual val SYNC_BASE_URL: String
  get() = window.location.origin
