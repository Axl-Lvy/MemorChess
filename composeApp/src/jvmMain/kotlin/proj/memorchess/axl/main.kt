package proj.memorchess.axl

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import proj.memorchess.axl.ui.KoinStarterApp

fun main() = application {
  Window(onCloseRequest = ::exitApplication, alwaysOnTop = true, title = "Memor Chess") {
    KoinStarterApp()
  }
}
