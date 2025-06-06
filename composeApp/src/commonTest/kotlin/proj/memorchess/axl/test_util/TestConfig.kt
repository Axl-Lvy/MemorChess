package proj.memorchess.axl.test_util

import kotlin.time.Duration
import proj.memorchess.axl.core.config.IAppConfig

/** Test configuration with values optimized for testing. */
object TestConfig : IAppConfig {
  override val minimumLoadingTime = Duration.ZERO
}
