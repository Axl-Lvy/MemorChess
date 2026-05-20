package proj.memorchess.axl

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import androidx.navigation.ExperimentalBrowserHistoryApi
import androidx.navigation.bindToBrowserNavigation
import kotlinx.browser.document
import org.koin.compose.KoinApplication
import org.koin.dsl.koinConfiguration
import proj.memorchess.axl.ui.App

@OptIn(ExperimentalComposeUiApi::class, ExperimentalBrowserHistoryApi::class)
fun main() {
  ComposeViewport(document.body ?: return) {
    KoinApplication(configuration = koinConfiguration { modules(*initKoinModules()) }) {
      App { it.callDelegate { navHostController -> navHostController.bindToBrowserNavigation() } }
    }
  }
}
