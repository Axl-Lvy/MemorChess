package proj.memorchess.axl.core.sync

import kotlinx.browser.window

/**
 * Web build serves from whatever origin hosts it (the mini PC domain in production,
 * `localhost:<port>` under `:composeApp:wasmJsRun`), so the origin itself is the base URL.
 */
actual val SYNC_BASE_URL: String
  get() = window.location.origin
