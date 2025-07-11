package proj.memorchess.axl.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import compose.icons.FeatherIcons
import compose.icons.feathericons.CheckCircle
import kotlinx.coroutines.launch
import proj.memorchess.axl.core.data.online.signIn
import proj.memorchess.axl.ui.theme.goodTint

@Composable
fun SignInButton(
    modifier: Modifier = Modifier,
    isSignedIn: Boolean,
    onSignedIn: () -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var signInError by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    val buttonColor =
        if (isSignedIn) goodTint.copy(alpha = 0.15f) else MaterialTheme.colorScheme.primary
    Button(
        onClick = { if (!isSignedIn) showDialog = true },
        modifier = modifier.fillMaxWidth().padding(bottom = 8.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = buttonColor,
            disabledContainerColor = buttonColor
        ),
        enabled = !isSignedIn,
        shape = ButtonDefaults.shape,
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp),
    ) {
        if (isSignedIn) {
            val icon = FeatherIcons.CheckCircle
            Icon(icon, contentDescription = icon.name, tint = goodTint,
                    modifier = Modifier.padding(end = 8.dp),)
            Text("Signed in", color = goodTint)
        } else {
            val icon = Icons.Filled.AccountCircle
            Icon(icon, contentDescription = icon.name, tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.padding(end = 8.dp),)
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
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (signInError != null) {
                        Text(signInError!!, color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    coroutineScope.launch {
                        try {
                            signIn(email, password)
                            onSignedIn()
                            showDialog = false
                            signInError = null
                        } catch (e: Exception) {
                            signInError = e.message ?: "Sign in failed"
                        }
                    }
                }) {
                    Text("Sign In")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

