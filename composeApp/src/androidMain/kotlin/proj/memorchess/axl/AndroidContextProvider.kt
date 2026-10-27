package proj.memorchess.axl

import android.content.Context

/**
 * Holds the process wide application [Context] for the Android actuals. [initialize] must be called
 * before composing [proj.memorchess.axl.ui.KoinStarterApp].
 */
object AndroidContextProvider {
  private var applicationContext: Context? = null

  /** Registers the application context of [context]. Safe to call multiple times. */
  fun initialize(context: Context) {
    applicationContext = context.applicationContext
  }

  internal val context: Context
    get() =
      checkNotNull(applicationContext) {
        "AndroidContextProvider.initialize(context) was not called before using the library."
      }
}
