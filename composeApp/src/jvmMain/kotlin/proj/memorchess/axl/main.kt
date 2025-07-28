package proj.memorchess.axl

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main(args: Array<String>) = application {
  Window(onCloseRequest = ::exitApplication, alwaysOnTop = true, title = "Memor Chess") {
    KoinStarterApp()
  }
}
