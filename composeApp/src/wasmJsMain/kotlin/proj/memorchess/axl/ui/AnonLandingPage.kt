package proj.memorchess.axl.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import proj.memorchess.axl.ui.components.buttons.SignInButton

/**
 * Landing page for anonymous users showing that the application is under construction.
 *
 * Displays a construction message with a chess animation and provides a sign-in option for beta
 * users.
 */
@Composable
fun AnonLandingPage() {
  val scrollState = rememberScrollState()
  Surface(modifier = Modifier.fillMaxSize().verticalScroll(scrollState), color = MaterialTheme.colorScheme.background) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.widthIn(max = 600.dp).padding(32.dp),
      ) {
        Text(
          text = "Under Construction",
          style = MaterialTheme.typography.headlineLarge,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.primary,
          textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(24.dp))

        ProgressIndicator(modifier = Modifier.size(300.dp))

        Spacer(modifier = Modifier.height(32.dp))

        Text(
          text = "Anki Chess is currently under development.",
          style = MaterialTheme.typography.titleLarge,
          textAlign = TextAlign.Center,
          color = MaterialTheme.colorScheme.onBackground,
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
          text =
            "We're building something amazing to help you memorize chess openings using spaced repetition!",
          style = MaterialTheme.typography.bodyLarge,
          textAlign = TextAlign.Center,
          color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
        )

        Spacer(modifier = Modifier.height(32.dp))

        Surface(
          modifier = Modifier.fillMaxWidth(),
          color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
          shape = MaterialTheme.shapes.medium,
        ) {
          Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
          ) {
            Text(
              text = "Have a beta account?",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.SemiBold,
              color = MaterialTheme.colorScheme.onPrimaryContainer,
              textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
              text = "Sign in to access early features and help shape the future of Anki Chess!",
              style = MaterialTheme.typography.bodyMedium,
              textAlign = TextAlign.Center,
              color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.9f),
            )

            Spacer(modifier = Modifier.height(20.dp))

            SignInButton(modifier = Modifier.widthIn(max = 300.dp))
          }
        }
      }
    }
  }
}

/**
 * Displays a circular progress indicator centered within the given modifier.
 *
 * @param modifier Modifier to be applied to the Box containing the progress indicator.
 */
@Composable
private fun ProgressIndicator(modifier: Modifier = Modifier) {
  Box(modifier = modifier) { CircularProgressIndicator(modifier = Modifier.fillMaxSize()) }
}
