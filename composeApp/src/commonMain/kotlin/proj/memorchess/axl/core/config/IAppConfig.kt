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
  var minimumLoadingTime: Duration

  var onSuccessDateFactor: Double

  companion object {
    private var instance: IAppConfig? = null

    /** Get the current configuration instance. */
    fun get(): IAppConfig {
      if (instance == null) {
        instance = StandardAppConfig
      }
      return instance!!
    }

    /**
     * Set the configuration instance. This should only be used for testing or during application
     * initialization.
     */
    fun replaceConfig(config: IAppConfig) {
      instance = config
    }
  }
}
