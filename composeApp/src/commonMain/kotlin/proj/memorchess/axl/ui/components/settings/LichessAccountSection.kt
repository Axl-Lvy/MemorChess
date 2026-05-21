package proj.memorchess.axl.ui.components.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import proj.memorchess.axl.core.auth.LichessAccount
import proj.memorchess.axl.core.auth.LichessSignInController
import proj.memorchess.axl.core.auth.OAuthTokenStore
import proj.memorchess.axl.core.auth.SignInResult

/**
 * Settings row for the Lichess account.
 *
 * Shows the username when signed in (with a Sign out button) or a Sign in button otherwise. The
 * sign in button kicks off the platform OAuth flow via [LichessSignInController].
 */
@Composable
fun LichessAccountSection(
  signInController: LichessSignInController = koinInject(),
  tokenStore: OAuthTokenStore = koinInject(),
) {
  val account by tokenStore.account.collectAsState()
  val scope = rememberCoroutineScope()
  var pending by remember { mutableStateOf(false) }
  var lastError by remember { mutableStateOf<String?>(null) }

  LichessAccountSectionContent(
    account = account,
    pending = pending,
    lastError = lastError,
    onSignIn = {
      pending = true
      lastError = null
      scope.launch {
        when (val result = signInController.signIn()) {
          SignInResult.Success -> Unit
          SignInResult.Cancelled -> lastError = "Sign in cancelled"
          is SignInResult.Failed -> lastError = result.message
        }
        pending = false
      }
    },
    onSignOut = {
      signInController.signOut()
      lastError = null
    },
  )
}

/** Stateless variant for previews and tests. */
@Composable
internal fun LichessAccountSectionContent(
  account: LichessAccount?,
  pending: Boolean,
  lastError: String?,
  onSignIn: () -> Unit,
  onSignOut: () -> Unit,
) {
  Card(modifier = Modifier.fillMaxWidth().testTag("lichess_account_section")) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Text(text = "Lichess account", style = MaterialTheme.typography.titleMedium)
      if (account != null) {
        Text(
          text = account.username?.let { "Signed in as $it" } ?: "Signed in",
          modifier = Modifier.testTag("lichess_account_username"),
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
          Button(
            onClick = onSignOut,
            modifier = Modifier.testTag("lichess_sign_out_button"),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
          ) {
            Text("Sign out")
          }
        }
      } else {
        Text(
          text =
            "Sign in to enable the Lichess opening explorer. Required since Lichess gated the " +
              "explorer behind authentication.",
          style = MaterialTheme.typography.bodySmall,
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
          Button(
            onClick = onSignIn,
            enabled = !pending,
            modifier = Modifier.testTag("lichess_sign_in_button"),
          ) {
            Text(if (pending) "Signing in..." else "Sign in with Lichess")
          }
        }
      }
      if (lastError != null) {
        Text(
          text = lastError,
          color = MaterialTheme.colorScheme.error,
          style = MaterialTheme.typography.bodySmall,
          modifier = Modifier.testTag("lichess_account_error"),
        )
      }
    }
  }
}
