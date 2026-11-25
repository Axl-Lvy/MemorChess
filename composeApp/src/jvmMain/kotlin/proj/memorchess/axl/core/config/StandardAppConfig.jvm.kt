package proj.memorchess.axl.core.config

import com.russhwolf.settings.Settings
import proj.memorchess.axl.dev.currentDevLaunchConfig
import proj.memorchess.axl.dev.resolveSettings

actual fun getPlatformSpecificSettings(): Settings = resolveSettings(currentDevLaunchConfig)
