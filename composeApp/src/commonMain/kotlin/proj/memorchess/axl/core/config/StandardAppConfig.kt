package proj.memorchess.axl.core.config

import com.russhwolf.settings.Settings
import com.russhwolf.settings.get
import com.russhwolf.settings.set
import kotlin.time.Duration.Companion.milliseconds

object StandardAppConfig : IAppConfig {
  override var minimumLoadingTime
    get() = settings["minimumLoadingTime", 500].milliseconds
    set(value) {
      settings["minimumLoadingTime"] = value.inWholeMilliseconds
    }
  override var onSuccessDateFactor: Double
    get() = settings["onSuccessDateFactor", 1.5]
    set(value) {
      settings["onSuccessDateFactor"] = value
    }
}

expect val settings: Settings
