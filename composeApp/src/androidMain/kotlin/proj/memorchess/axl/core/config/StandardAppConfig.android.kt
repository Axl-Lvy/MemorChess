package proj.memorchess.axl.core.config

import android.content.Context
import com.russhwolf.settings.Settings
import com.russhwolf.settings.SharedPreferencesSettings
import proj.memorchess.axl.AndroidContextProvider

actual fun getPlatformSpecificSettings(): Settings =
  SharedPreferencesSettings(
    AndroidContextProvider.context.getSharedPreferences("memorchess_settings", Context.MODE_PRIVATE)
  )
