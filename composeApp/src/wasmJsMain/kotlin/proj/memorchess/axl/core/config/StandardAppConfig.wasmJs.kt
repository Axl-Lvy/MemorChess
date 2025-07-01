package proj.memorchess.axl.core.config

import com.russhwolf.settings.Settings
import com.russhwolf.settings.StorageSettings

actual val settings: Settings = StorageSettings() // Use localStorage by default
