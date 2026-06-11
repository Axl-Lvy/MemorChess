package proj.memorchess.axl

import android.content.Context

/**
 * Holds the process wide application [Context] used by the Android actuals of this library.
 *
 * The host application must call [initialize] before composing
 * [proj.memorchess.axl.ui.KoinStarterApp].
 */
object AndroidContextProvider {
  private var applicationContext: Context? = null

  /** Registers the application context of [context]. Safe to call multiple times. */
  fun initialize(context: Context) {
    applicationContext = context.applicationContext
  }

  /** The registered application context. */
  internal val context: Context
    get() =
      checkNotNull(applicationContext) {
        "AndroidContextProvider.initialize(context) was not called before using the library."
      }
}
