package proj.memorchess.axl.ui.components.loading

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
fun LoadingPage(suspendingFunction: suspend () -> Any?, composable: @Composable () -> Unit) {
  var isLoading by remember { mutableStateOf(true) }

  LaunchedEffect(Unit) {
    suspendingFunction()
    isLoading = false
  }

  if (isLoading) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
      CircularProgressIndicator(modifier = Modifier.fillMaxSize())
    }
  } else {
    composable()
  }
}
