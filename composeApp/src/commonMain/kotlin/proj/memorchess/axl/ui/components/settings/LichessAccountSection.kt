package proj.memorchess.axl.ui.components.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import proj.memorchess.axl.core.auth.LichessAccount
import proj.memorchess.axl.core.auth.LichessSignInController
import proj.memorchess.axl.core.auth.OAuthTokenStore
import proj.memorchess.axl.core.auth.SignInResult
import proj.memorchess.axl.ui.components.buttons.KineticButton
import proj.memorchess.axl.ui.components.buttons.KineticButtonStyle
import proj.memorchess.axl.ui.theme.LocalKineticPalette
import proj.memorchess.axl.ui.theme.LocalKineticTypography

/**
 * Lichess account row.
 *
 * Visual layer follows the Kinetic settings card: a 56.dp square cyan-tinted avatar (showing the
 * first letter of the username, or "?" when signed out), the username on top in `display`, a small
 * mono status line below, and a [Row] of [KineticButton]s on the right (sign-in / sign-out).
 *
 * All OAuth wiring is unchanged from the previous Material version — only the rendering changed.
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
  val palette = LocalKineticPalette.current
  val typography = LocalKineticTypography.current
  val signedIn = account != null

  Column(
    modifier = Modifier.fillMaxWidth().testTag("lichess_account_section"),
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      LichessAvatar(initial = account?.username?.firstOrNull()?.uppercaseChar()?.toString() ?: "?")

      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = account?.username ?: "Not signed in",
          style = typography.display.copy(color = palette.ink, fontSize = 16.sp),
          modifier = if (signedIn) Modifier.testTag("lichess_account_username") else Modifier,
        )
        Text(
          text =
            if (signedIn) {
              "Connected to lichess.org"
            } else {
              "Sign in to enable the Lichess opening explorer."
            },
          style = typography.monoSm.copy(color = palette.ink3),
        )
      }

      if (signedIn) {
        KineticButton(
          onClick = onSignOut,
          style = KineticButtonStyle.DangerOutline,
          modifier = Modifier.testTag("lichess_sign_out_button"),
        ) {
          Text(text = "DISCONNECT")
        }
      } else {
        KineticButton(
          onClick = onSignIn,
          enabled = !pending,
          style = KineticButtonStyle.Default,
          modifier = Modifier.testTag("lichess_sign_in_button"),
        ) {
          Text(text = if (pending) "SIGNING IN…" else "SIGN IN")
        }
      }
    }

    if (lastError != null) {
      Text(
        text = lastError,
        style = typography.bodySm.copy(color = palette.red),
        modifier = Modifier.testTag("lichess_account_error"),
      )
    }
  }
}

/** Small 56.dp square avatar with the user's initial; mirrors `.lichess-avatar` from the HTML. */
@Composable
private fun LichessAvatar(initial: String) {
  val palette = LocalKineticPalette.current
  val typography = LocalKineticTypography.current
  Box(
    modifier =
      Modifier.size(56.dp)
        .background(color = palette.cyan)
        .border(width = 1.dp, color = palette.lineBright),
    contentAlignment = Alignment.Center,
  ) {
    Text(text = initial, style = typography.brand.copy(color = palette.bg, fontSize = 22.sp))
  }
}
