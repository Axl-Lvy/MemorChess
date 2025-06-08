package proj.memorchess.axl.ui.components.loading

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlin.time.measureTime
import proj.memorchess.axl.core.config.IAppConfig

/**
 * A composable that displays a loading indicator while a suspending function is executed. Once the
 * function completes, it displays the provided composable content.
 *
 * This composable ensures that the loading indicator is shown for at least the
 * [minimum duration specified in the application configuration][IAppConfig.minimumLoadingTime].
 *
 * @param suspendingFunction The suspending function to execute.
 * @param composable The composable content to display after loading.
 */
@Composable
fun LoadingWidget(suspendingFunction: suspend () -> Any?, composable: @Composable () -> Unit) {
  var isLoading by remember { mutableStateOf(true) }

  LaunchedEffect(Unit) {
    val minimumLoadingTime = IAppConfig.get().minimumLoadingTime
    val timeTaken = measureTime { suspendingFunction() }
    if (timeTaken < minimumLoadingTime) {
      kotlinx.coroutines.delay(minimumLoadingTime - timeTaken)
    }
    isLoading = false
  }

  if (isLoading) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
      CircularProgressIndicator(modifier = Modifier.then(Modifier.size(200.dp)))
    }
  } else {
    composable()
  }
}
