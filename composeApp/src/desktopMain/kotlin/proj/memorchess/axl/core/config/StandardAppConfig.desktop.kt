package proj.memorchess.axl.core.config

import com.russhwolf.settings.PropertiesSettings
import com.russhwolf.settings.Settings

private val delegate = PropertiesLoader.INSTANCE.properties

actual val SETTINGS: Settings = PropertiesSettings(delegate)
