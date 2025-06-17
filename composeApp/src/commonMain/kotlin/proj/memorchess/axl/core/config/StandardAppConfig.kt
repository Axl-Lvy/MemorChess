package proj.memorchess.axl.core.config

import kotlin.time.Duration.Companion.seconds

object StandardAppConfig : IAppConfig {
  override val minimumLoadingTime = 0.5.seconds
}
