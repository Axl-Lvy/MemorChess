package proj.memorchess.axl.core.config

import com.russhwolf.settings.PreferencesSettings
import com.russhwolf.settings.Settings
import java.util.prefs.Preferences

actual val SETTINGS: Settings
  get() = PreferencesSettings(Preferences.userRoot().node("proj/memorchess/axl"))
