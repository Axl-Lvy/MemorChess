package proj.memorchess.axl.core.config

import kotlin.time.Duration

/**
 * Interface for application configuration. This allows different configurations for different
 * environments (e.g., production, test).
 */
interface IAppConfig {
  /**
   * The minimum time the loading indicator should be displayed, even if the loading operation
   * completes faster.
   */
  val minimumLoadingTime: Duration

  companion object {
    private var instance: IAppConfig = StandardAppConfig

    /** Get the current configuration instance. */
    fun get(): IAppConfig = instance

    /**
     * Set the configuration instance. This should only be used for testing or during application
     * initialization.
     */
    fun set(config: IAppConfig) {
      instance = config
    }
  }
}
