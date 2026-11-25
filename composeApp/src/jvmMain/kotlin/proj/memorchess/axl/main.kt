package proj.memorchess.axl

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import proj.memorchess.axl.ui.KoinStarterApp

fun main() = launchDesktopWindow("Memor Chess")

/** Opens the desktop window titled [title] and blocks until it is closed. */
internal fun launchDesktopWindow(title: String) = application {
  Window(onCloseRequest = ::exitApplication, alwaysOnTop = true, title = title) { KoinStarterApp() }
}
