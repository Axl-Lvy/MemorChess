package proj.memorchess.axl.core.config

import android.content.Context
import com.russhwolf.settings.Settings
import com.russhwolf.settings.SharedPreferencesSettings
import proj.memorchess.axl.MAIN_ACTIVITY

private val delegate = MAIN_ACTIVITY.getPreferences(Context.MODE_PRIVATE)

actual fun getPlatformSpecificSettings(): Settings = SharedPreferencesSettings(delegate)
