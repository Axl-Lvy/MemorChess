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
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTime

/**
 * A composable that displays a loading indicator while a suspending function is executed. Once the
 * function completes, it displays the provided composable content.
 *
 * This composable ensures that the loading indicator is shown for at least
 * [a minimum duration][MINIMUM_LOADING_TIME]
 *
 * @param suspendingFunction The suspending function to execute.
 * @param composable The composable content to display after loading.
 */
@Composable
fun LoadingPage(suspendingFunction: suspend () -> Any?, composable: @Composable () -> Unit) {

  var isLoading by remember { mutableStateOf(true) }

  LaunchedEffect(Unit) {
    val timeTaken = measureTime { suspendingFunction() }
    if (timeTaken < MINIMUM_LOADING_TIME) {
      kotlinx.coroutines.delay(MINIMUM_LOADING_TIME - timeTaken)
    }
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

/**
 * The minimum time the loading indicator should be displayed, even if the suspending function
 * completes faster.
 */
private val MINIMUM_LOADING_TIME = 1.seconds
