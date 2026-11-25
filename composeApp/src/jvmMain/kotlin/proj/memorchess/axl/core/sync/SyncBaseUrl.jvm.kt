package proj.memorchess.axl.core.sync

import proj.memorchess.axl.dev.DEV_SYNC_URL_PROPERTY
import proj.memorchess.axl.dev.currentDevLaunchConfig
import proj.memorchess.axl.dev.resolveSyncBaseUrl

// A dev launch never reaches production: see resolveSyncBaseUrl.
actual val SYNC_BASE_URL: String =
  resolveSyncBaseUrl(currentDevLaunchConfig, System.getProperty(DEV_SYNC_URL_PROPERTY))
