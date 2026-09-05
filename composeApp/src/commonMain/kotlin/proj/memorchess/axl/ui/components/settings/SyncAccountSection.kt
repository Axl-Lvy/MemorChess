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
import memorchess.composeapp.generated.resources.sync_connected
import memorchess.composeapp.generated.resources.sync_disconnect
import memorchess.composeapp.generated.resources.sync_not_signed_in
import memorchess.composeapp.generated.resources.sync_sign_in
import memorchess.composeapp.generated.resources.sync_sign_in_cancelled
import memorchess.composeapp.generated.resources.sync_sign_in_prompt
import memorchess.composeapp.generated.resources.sync_signing_in
import memorchess.composeapp.generated.resources.sync_status_backing_off
import memorchess.composeapp.generated.resources.sync_status_idle
import memorchess.composeapp.generated.resources.sync_status_paused_no_auth
import memorchess.composeapp.generated.resources.sync_status_syncing
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import proj.memorchess.axl.core.auth.Account
import proj.memorchess.axl.core.auth.AuthProvider
import proj.memorchess.axl.core.auth.SignInResult
import proj.memorchess.axl.core.sync.SyncEngine
import proj.memorchess.axl.core.sync.SyncJobStatus
import proj.memorchess.axl.ui.components.buttons.KineticButton
import proj.memorchess.axl.ui.components.buttons.KineticButtonStyle
import proj.memorchess.axl.ui.theme.LocalKineticPalette
import proj.memorchess.axl.ui.theme.LocalKineticTypography

/** Holds a sign-in error as a [StringResource] plus an optional format argument. */
private data class SyncSignInError(val res: StringResource, val arg: String?)

/**
 * Sync account row: sign in / sign out against [authProvider], the current account's identity, and
 * [syncEngine]'s status with a "sync now" action.
 *
 * Visual layer mirrors [LichessAccountSection]: a 56.dp square avatar (first letter of the display
 * name, or "?" when signed out), the account name on top, a status line below, and a
 * [KineticButton] on the right.
 */
@Composable
fun SyncAccountSection(
  authProvider: AuthProvider = koinInject(),
  syncEngine: SyncEngine = koinInject(),
) {
  val account by authProvider.currentAccount.collectAsState()
  val status by syncEngine.status.collectAsState()
  val scope = rememberCoroutineScope()
  var pending by remember { mutableStateOf(false) }
  var lastError by remember { mutableStateOf<SyncSignInError?>(null) }
  val lastErrorText: String? = lastError?.let { err ->
    if (err.arg != null) stringResource(err.res, err.arg) else stringResource(err.res)
  }

  SyncAccountSectionContent(
    account = account,
    pending = pending,
    lastError = lastErrorText,
    status = status,
    onSignIn = {
      pending = true
      lastError = null
      scope.launch {
        when (val result = authProvider.signIn()) {
          SignInResult.Success -> syncEngine.syncNow()
          SignInResult.Cancelled ->
            lastError = SyncSignInError(Res.string.sync_sign_in_cancelled, null)
          is SignInResult.Failed -> lastError = SyncSignInError(result.message, result.arg)
        }
        pending = false
      }
    },
    onSignOut = {
      authProvider.signOut()
      lastError = null
    },
  )
}

/** Stateless variant for previews and tests. */
@Composable
internal fun SyncAccountSectionContent(
  account: Account?,
  pending: Boolean,
  lastError: String?,
  status: SyncJobStatus,
  onSignIn: () -> Unit,
  onSignOut: () -> Unit,
) {
  val palette = LocalKineticPalette.current
  val typography = LocalKineticTypography.current
  val signedIn = account != null

  Column(
    modifier = Modifier.fillMaxWidth().testTag("sync_account_section"),
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      SyncAvatar(initial = avatarInitial(account))
      SyncAccountIdentity(account = account, signedIn = signedIn, modifier = Modifier.weight(1f))
      SyncAccountAction(
        signedIn = signedIn,
        pending = pending,
        onSignIn = onSignIn,
        onSignOut = onSignOut,
      )
    }

    Text(
      text = stringResource(status.toStringResource()),
      style = typography.monoSm.copy(color = palette.ink3),
      modifier = Modifier.testTag("sync_status_line"),
    )

    if (lastError != null) {
      Text(
        text = lastError,
        style = typography.bodySm.copy(color = palette.destructive),
        modifier = Modifier.testTag("sync_account_error"),
      )
    }
  }
}

/**
 * Display string for one [SyncJobStatus] value. Exhaustive `when`: a new status must be added here,
 * per project convention for a state machine reaching a UI consumer.
 */
private fun SyncJobStatus.toStringResource(): StringResource =
  when (this) {
    SyncJobStatus.IDLE -> Res.string.sync_status_idle
    SyncJobStatus.SCHEDULED,
    SyncJobStatus.RUNNING -> Res.string.sync_status_syncing
    SyncJobStatus.BACKING_OFF -> Res.string.sync_status_backing_off
    SyncJobStatus.PAUSED_NO_AUTH -> Res.string.sync_status_paused_no_auth
  }

/** The account's display name, falling back to [Account.sub] when no name was decoded. */
private fun displayName(account: Account): String = account.name ?: account.sub

/** First letter of the display name uppercased, or "?" when signed out. */
private fun avatarInitial(account: Account?): String =
  account?.let(::displayName)?.firstOrNull()?.uppercaseChar()?.toString() ?: "?"

/** Account name and status lines shown between the avatar and the action button. */
@Composable
private fun SyncAccountIdentity(
  account: Account?,
  signedIn: Boolean,
  modifier: Modifier = Modifier,
) {
  val palette = LocalKineticPalette.current
  val typography = LocalKineticTypography.current
  Column(modifier = modifier) {
    Text(
      text = account?.let(::displayName) ?: stringResource(Res.string.sync_not_signed_in),
      style = typography.display.copy(color = palette.ink, fontSize = 16.sp),
      modifier = if (signedIn) Modifier.testTag("sync_account_name") else Modifier,
    )
    Text(
      text =
        if (signedIn) {
          stringResource(Res.string.sync_connected)
        } else {
          stringResource(Res.string.sync_sign_in_prompt)
        },
      style = typography.monoSm.copy(color = palette.ink3),
    )
  }
}

/** Sign-in / sign-out button, switching on [signedIn]. */
@Composable
private fun SyncAccountAction(
  signedIn: Boolean,
  pending: Boolean,
  onSignIn: () -> Unit,
  onSignOut: () -> Unit,
) {
  if (signedIn) {
    KineticButton(
      onClick = onSignOut,
      style = KineticButtonStyle.DangerOutline,
      modifier = Modifier.testTag("sync_sign_out_button"),
    ) {
      Text(text = stringResource(Res.string.sync_disconnect))
    }
  } else {
    KineticButton(
      onClick = onSignIn,
      enabled = !pending,
      style = KineticButtonStyle.Default,
      modifier = Modifier.testTag("sync_sign_in_button"),
    ) {
      Text(
        text =
          if (pending) stringResource(Res.string.sync_signing_in)
          else stringResource(Res.string.sync_sign_in)
      )
    }
  }
}

/** Small 56.dp square avatar with the account's initial. */
@Composable
private fun SyncAvatar(initial: String) {
  val palette = LocalKineticPalette.current
  val typography = LocalKineticTypography.current
  Box(
    modifier =
      Modifier.size(56.dp)
        .background(color = palette.streak)
        .border(width = 1.dp, color = palette.lineBright),
    contentAlignment = Alignment.Center,
  ) {
    Text(text = initial, style = typography.brand.copy(color = palette.bg, fontSize = 22.sp))
  }
}
