package proj.memorchess.axl

import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import androidx.navigation.ExperimentalBrowserHistoryApi
import androidx.navigation.bindToBrowserNavigation
import kotlinx.browser.document
import org.koin.compose.KoinApplication
import org.koin.compose.koinInject
import proj.memorchess.axl.core.data.online.auth.AuthManager
import proj.memorchess.axl.ui.App
import proj.memorchess.axl.ui.initComposableModules

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
  ComposeViewport(document.body ?: return) {
    val modules = initKoinModules() + initComposableModules()
    KoinApplication(application = { modules(*modules) }) { NoAccountProtection() }
  }
}

@OptIn(ExperimentalBrowserHistoryApi::class)
@Composable
private fun NoAccountProtection(authManager: AuthManager = koinInject()) {
  if (authManager.user == null) {
    AnonLandingPage()
  } else {
    App { it.callDelegate { navHostController -> navHostController.bindToBrowserNavigation() } }
  }
}
