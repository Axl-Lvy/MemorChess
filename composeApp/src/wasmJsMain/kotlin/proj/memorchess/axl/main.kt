package proj.memorchess.axl

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import androidx.navigation.ExperimentalBrowserHistoryApi
import androidx.navigation.bindToBrowserNavigation
import kotlinx.browser.document
import org.koin.compose.KoinApplication
import proj.memorchess.axl.ui.App
import proj.memorchess.axl.ui.initComposableModules

@OptIn(ExperimentalComposeUiApi::class, ExperimentalBrowserHistoryApi::class)
fun main() {
  ComposeViewport(document.body ?: return) {
    val modules = initKoinModules() + initComposableModules()
    KoinApplication(application = { modules(*modules) }) {
      App { it.callDelegate { navHostController -> navHostController.bindToBrowserNavigation() } }
    }
  }
}
