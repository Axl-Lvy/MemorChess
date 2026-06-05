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
import memorchess.composeapp.generated.resources.Res
import memorchess.composeapp.generated.resources.lichess_connected
import memorchess.composeapp.generated.resources.lichess_disconnect
import memorchess.composeapp.generated.resources.lichess_not_signed_in
import memorchess.composeapp.generated.resources.lichess_sign_in
import memorchess.composeapp.generated.resources.lichess_sign_in_cancelled
import memorchess.composeapp.generated.resources.lichess_sign_in_prompt
import memorchess.composeapp.generated.resources.lichess_signing_in
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import proj.memorchess.axl.core.auth.LichessAccount
import proj.memorchess.axl.core.auth.LichessSignInController
import proj.memorchess.axl.core.auth.OAuthTokenStore
import proj.memorchess.axl.core.auth.SignInResult
import proj.memorchess.axl.ui.components.buttons.KineticButton
import proj.memorchess.axl.ui.components.buttons.KineticButtonStyle
import proj.memorchess.axl.ui.theme.LocalKineticPalette
import proj.memorchess.axl.ui.theme.LocalKineticTypography

/** Holds a sign-in error as a [StringResource] plus an optional format argument. */
private data class SignInError(val res: StringResource, val arg: String?)

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
  var lastError by remember { mutableStateOf<SignInError?>(null) }
  val lastErrorText: String? = lastError?.let { err ->
    if (err.arg != null) stringResource(err.res, err.arg) else stringResource(err.res)
  }

  LichessAccountSectionContent(
    account = account,
    pending = pending,
    lastError = lastErrorText,
    onSignIn = {
      pending = true
      lastError = null
      scope.launch {
        when (val result = signInController.signIn()) {
          SignInResult.Success -> Unit
          SignInResult.Cancelled ->
            lastError = SignInError(Res.string.lichess_sign_in_cancelled, null)
          is SignInResult.Failed -> lastError = SignInError(result.message, result.arg)
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
      LichessAvatar(initial = avatarInitial(account))
      LichessAccountIdentity(account = account, signedIn = signedIn, modifier = Modifier.weight(1f))
      LichessAccountAction(
        signedIn = signedIn,
        pending = pending,
        onSignIn = onSignIn,
        onSignOut = onSignOut,
      )
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

/** First letter of the username uppercased, or "?" when signed out. */
private fun avatarInitial(account: LichessAccount?): String =
  account?.username?.firstOrNull()?.uppercaseChar()?.toString() ?: "?"

/** Username and connection-status lines shown between the avatar and the action button. */
@Composable
private fun LichessAccountIdentity(
  account: LichessAccount?,
  signedIn: Boolean,
  modifier: Modifier = Modifier,
) {
  val palette = LocalKineticPalette.current
  val typography = LocalKineticTypography.current
  Column(modifier = modifier) {
    Text(
      text = account?.username ?: stringResource(Res.string.lichess_not_signed_in),
      style = typography.display.copy(color = palette.ink, fontSize = 16.sp),
      modifier = if (signedIn) Modifier.testTag("lichess_account_username") else Modifier,
    )
    Text(
      text =
        if (signedIn) {
          stringResource(Res.string.lichess_connected)
        } else {
          stringResource(Res.string.lichess_sign_in_prompt)
        },
      style = typography.monoSm.copy(color = palette.ink3),
    )
  }
}

/** Sign-in / sign-out button, switching on [signedIn]. */
@Composable
private fun LichessAccountAction(
  signedIn: Boolean,
  pending: Boolean,
  onSignIn: () -> Unit,
  onSignOut: () -> Unit,
) {
  if (signedIn) {
    KineticButton(
      onClick = onSignOut,
      style = KineticButtonStyle.DangerOutline,
      modifier = Modifier.testTag("lichess_sign_out_button"),
    ) {
      Text(text = stringResource(Res.string.lichess_disconnect))
    }
  } else {
    KineticButton(
      onClick = onSignIn,
      enabled = !pending,
      style = KineticButtonStyle.Default,
      modifier = Modifier.testTag("lichess_sign_in_button"),
    ) {
      Text(
        text =
          if (pending) stringResource(Res.string.lichess_signing_in)
          else stringResource(Res.string.lichess_sign_in)
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
