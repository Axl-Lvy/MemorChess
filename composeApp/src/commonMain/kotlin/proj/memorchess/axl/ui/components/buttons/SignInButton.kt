package proj.memorchess.axl.ui.components.buttons

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import compose.icons.FeatherIcons
import compose.icons.feathericons.CheckCircle
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import proj.memorchess.axl.core.config.KEEP_LOGGED_IN_SETTING
import proj.memorchess.axl.core.data.online.auth.AuthManager
import proj.memorchess.axl.core.data.online.database.DatabaseSynchronizer
import proj.memorchess.axl.ui.components.popup.ConfirmationDialog
import proj.memorchess.axl.ui.theme.goodTint

@Composable
fun SignInButton(modifier: Modifier = Modifier, authManager: AuthManager = koinInject()) {
  var showSignInDialog by rememberSaveable { mutableStateOf(false) }
  val signOutDialog = remember { ConfirmationDialog("Sign Out", "Cancel") }
  val staySignedInDialog = remember { ConfirmationDialog("Yes", "No") }
  val coroutineScope = rememberCoroutineScope()
  val isSignedIn = authManager.user != null

  signOutDialog.DrawDialog()
  var canShowStaySignedInDialog by rememberSaveable { mutableStateOf(false) }
  staySignedInDialog.DrawDialog()

  if (canShowStaySignedInDialog) {
    if (authManager.user != null) {
      staySignedInDialog.show("Stay signed in?") {
        KEEP_LOGGED_IN_SETTING.setValue(true)
        authManager.updateSavedTokens()
      }
    }
    canShowStaySignedInDialog = false
  }

  val buttonColor =
    if (isSignedIn) goodTint.copy(alpha = 0.15f) else MaterialTheme.colorScheme.primary
  Button(
    onClick = {
      if (!isSignedIn) {
        showSignInDialog = true
      } else {
        signOutDialog.show("Are you sure you want to sign out?") {
          coroutineScope.launch {
            KEEP_LOGGED_IN_SETTING.reset()
            authManager.signOut()
          }
        }
      }
    },
    modifier = modifier.fillMaxWidth().padding(bottom = 8.dp).testTag("sign_in_button"),
    colors =
      ButtonDefaults.buttonColors(
        containerColor = buttonColor,
        disabledContainerColor = buttonColor,
      ),
    enabled = true,
    shape = ButtonDefaults.shape,
    elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp),
  ) {
    if (isSignedIn) {
      val icon = FeatherIcons.CheckCircle
      Icon(
        icon,
        contentDescription = icon.name,
        tint = goodTint,
        modifier = Modifier.padding(end = 8.dp),
      )
      Text("Signed in", color = goodTint)
    } else {
      val icon = Icons.Filled.AccountCircle
      Icon(
        icon,
        contentDescription = icon.name,
        tint = MaterialTheme.colorScheme.onPrimary,
        modifier = Modifier.padding(end = 8.dp),
      )
      Text("Sign in", color = MaterialTheme.colorScheme.onPrimary)
    }
  }

  if (showSignInDialog) {
    KEEP_LOGGED_IN_SETTING.reset()
    SignInDialog(
      dismiss = {
        showSignInDialog = false
        canShowStaySignedInDialog = true
      }
    )
  }
}

@Composable
private fun SignInDialog(
  dismiss: () -> Unit,
  authManager: AuthManager = koinInject(),
  databaseSynchronizer: DatabaseSynchronizer = koinInject(),
) {
  val coroutineScope = rememberCoroutineScope()
  var email by rememberSaveable { mutableStateOf("") }
  var password by rememberSaveable { mutableStateOf("") }
  var signInError by rememberSaveable { mutableStateOf<String?>(null) }
  var isSigningIn by rememberSaveable { mutableStateOf(false) }
  AlertDialog(
    onDismissRequest = { dismiss() },
    title = { Text("Sign In") },
    text = {
      Column {
        OutlinedTextField(
          value = email,
          onValueChange = { email = it },
          label = { Text("Email") },
          modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
          value = password,
          onValueChange = { password = it },
          label = { Text("Password") },
          visualTransformation = PasswordVisualTransformation(),
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
          modifier = Modifier.fillMaxWidth(),
        )
        ErrorPayload(signInError)
      }
    },
    confirmButton = {
      TextButton(
        modifier = Modifier.testTag("sign_in_confirmation_button"),
        onClick = {
          coroutineScope.launch {
            isSigningIn = true
            try {
              authManager.signInFromEmail(email, password)
              databaseSynchronizer.getLastUpdates()
              signInError = null
            } catch (e: Exception) {
              signInError = e.message ?: "Sign in failed"
            }
            if (signInError == null) {
              dismiss()
              signInError = null
            }
            isSigningIn = false
          }
        },
        enabled = !isSigningIn,
      ) {
        if (isSigningIn) CircularProgressIndicator() else Text("Sign In")
      }
    },
    dismissButton = {
      TextButton(onClick = { dismiss() }, enabled = !isSigningIn) { Text("Cancel") }
    },
  )
}

@Composable
private fun ErrorPayload(signInError: String?) {
  if (signInError != null) {
    if (signInError.contains("Invalid login credentials")) {
      Text("Invalid email or password", color = MaterialTheme.colorScheme.error)
    }
    Text(signInError, color = MaterialTheme.colorScheme.error)
  }
}
