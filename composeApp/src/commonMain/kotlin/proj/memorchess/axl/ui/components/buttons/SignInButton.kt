package proj.memorchess.axl.ui.components.buttons

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import compose.icons.FeatherIcons
import compose.icons.feathericons.CheckCircle
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import proj.memorchess.axl.core.data.online.auth.AuthManager
import proj.memorchess.axl.core.data.online.database.DatabaseSynchronizer
import proj.memorchess.axl.ui.theme.goodTint

@Composable
fun SignInButton(
  modifier: Modifier = Modifier,
  authManager: AuthManager = koinInject(),
  databaseSynchronizer: DatabaseSynchronizer = koinInject(),
) {
  var showDialog by rememberSaveable { mutableStateOf(false) }
  var email by rememberSaveable { mutableStateOf("") }
  var password by rememberSaveable { mutableStateOf("") }
  var signInError by rememberSaveable { mutableStateOf<String?>(null) }
  val coroutineScope = rememberCoroutineScope()
  val isSignedIn = authManager.user != null

  val buttonColor =
    if (isSignedIn) goodTint.copy(alpha = 0.15f) else MaterialTheme.colorScheme.primary
  Button(
    onClick = { if (!isSignedIn) showDialog = true },
    modifier = modifier.fillMaxWidth().padding(bottom = 8.dp),
    colors =
      ButtonDefaults.buttonColors(
        containerColor = buttonColor,
        disabledContainerColor = buttonColor,
      ),
    enabled = !isSignedIn,
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

  if (showDialog) {
    AlertDialog(
      onDismissRequest = { showDialog = false },
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
          if (signInError != null) {
            if (signInError!!.contains("Invalid login credentials")) {
              Text("Invalid email or password", color = MaterialTheme.colorScheme.error)
            }
            Text(signInError!!, color = MaterialTheme.colorScheme.error)
          }
        }
      },
      confirmButton = {
        TextButton(
          onClick = {
            coroutineScope.launch {
              try {
                authManager.signInFromEmail(email, password)
                databaseSynchronizer.getLastUpdates()
                signInError = null
              } catch (e: Exception) {
                signInError = e.message ?: "Sign in failed"
              }
              if (signInError == null) {
                showDialog = false
                signInError = null
              }
            }
          }
        ) {
          Text("Sign In")
        }
      },
      dismissButton = { TextButton(onClick = { showDialog = false }) { Text("Cancel") } },
    )
  }
}
