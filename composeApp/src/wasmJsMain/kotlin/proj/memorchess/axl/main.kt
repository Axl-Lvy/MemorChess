package proj.memorchess.axl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.ComposeViewport
import kotlinx.browser.document
import org.koin.compose.KoinApplication
import org.koin.compose.koinInject
import proj.memorchess.axl.core.data.online.auth.AuthManager
import proj.memorchess.axl.ui.components.buttons.SignInButton

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
  ComposeViewport(document.body!!) {
    KoinApplication(application = { modules(*initKoinModules()) }) { NoAccountProtection() }
  }
}

@Composable
private fun NoAccountProtection(authManager: AuthManager = koinInject()) {
  if (authManager.user == null) {
    Column(verticalArrangement = Arrangement.Center, modifier = Modifier.fillMaxHeight()) {
      Box(contentAlignment = Alignment.Center) { SignInButton() }
    }
  } else {
    App()
  }
}
