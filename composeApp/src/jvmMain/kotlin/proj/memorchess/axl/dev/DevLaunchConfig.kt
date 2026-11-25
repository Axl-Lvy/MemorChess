package proj.memorchess.axl.dev

import com.russhwolf.settings.PreferencesSettings
import com.russhwolf.settings.PropertiesSettings
import com.russhwolf.settings.Settings
import java.io.File
import java.util.Properties
import java.util.prefs.Preferences
import proj.memorchess.axl.core.sync.PRODUCTION_SYNC_BASE_URL

/** System property holding the worktree's dev data directory; its presence marks a dev launch. */
internal const val DEV_DATA_DIR_PROPERTY = "memorchess.dev.dataDir"

/** System property holding the path of the local dev server's session file. */
internal const val DEV_SESSION_FILE_PROPERTY = "memorchess.dev.sessionFile"

/** System property the dev launch sets to the local dev server's base URL before Koin starts. */
internal const val DEV_SYNC_URL_PROPERTY = "memorchess.dev.syncUrl"

/** Base URL a dev launch syncs against when no dev server is known: it never resolves. */
internal const val UNREACHABLE_SYNC_BASE_URL = "https://chess.invalid"

/** Java Preferences node holding the settings of a normal (non-dev) desktop launch. */
internal const val SHARED_PREFERENCES_NODE = "proj/memorchess/axl"

/** Where a dev launch keeps its data and where it looks for the local dev server's session. */
internal data class DevLaunchConfig(val dataDir: File, val sessionFile: File?)

/** Returns the dev launch configuration, or `null` for a normal launch. */
internal fun resolveDevLaunchConfig(readProperty: (String) -> String?): DevLaunchConfig? {
  val dataDir = readProperty(DEV_DATA_DIR_PROPERTY) ?: return null
  return DevLaunchConfig(File(dataDir), readProperty(DEV_SESSION_FILE_PROPERTY)?.let(::File))
}

/** Returns the Room database file for this launch. */
internal fun resolveDatabaseFile(config: DevLaunchConfig?, tmpDir: File): File =
  if (config == null) File(tmpDir, "my_room.db") else File(config.dataDir, "memorchess.db")

/** Returns the sync server base URL; a dev launch never gets the production one. */
internal fun resolveSyncBaseUrl(config: DevLaunchConfig?, devSyncUrl: String?): String =
  when {
    config == null -> PRODUCTION_SYNC_BASE_URL
    devSyncUrl == null -> UNREACHABLE_SYNC_BASE_URL
    else -> devSyncUrl
  }

/** Returns the settings for this launch: a file inside the dev data dir, or the shared node. */
internal fun resolveSettings(config: DevLaunchConfig?): Settings {
  if (config == null) {
    return PreferencesSettings(Preferences.userRoot().node(SHARED_PREFERENCES_NODE))
  }
  val file = File(config.dataDir, "settings.properties")
  val properties = Properties()
  if (file.exists()) file.inputStream().use(properties::load)
  return PropertiesSettings(properties) { modified ->
    file.parentFile.mkdirs()
    file.outputStream().use { modified.store(it, null) }
  }
}

/** The dev launch configuration of this JVM, read once from the system properties. */
internal val currentDevLaunchConfig: DevLaunchConfig? by lazy {
  resolveDevLaunchConfig(System::getProperty)
}
