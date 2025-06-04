package proj.memorchess.axl.ui.pages

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ErrorPage(errorMessage: String, onRetry: () -> Unit) {
  Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
      modifier = Modifier.padding(16.dp),
    ) {
      Text(
        text = "Oops!",
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onError,
      )
      Spacer(modifier = Modifier.height(8.dp))
      Text(text = errorMessage, style = MaterialTheme.typography.bodyMedium, fontSize = 18.sp)
      Spacer(modifier = Modifier.height(16.dp))
      Button(onClick = onRetry) { Text(text = "Retry") }
    }
  }
}
