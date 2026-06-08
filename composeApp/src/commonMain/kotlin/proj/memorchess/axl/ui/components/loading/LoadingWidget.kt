package proj.memorchess.axl.ui.components.loading

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import kotlin.time.measureTime
import proj.memorchess.axl.core.config.MINIMUM_LOADING_TIME_SETTING
import proj.memorchess.axl.ui.theme.KineticMotion

/**
 * A composable that displays a loading indicator while a suspending function is executed. Once the
 * function completes, it displays the provided composable content.
 *
 * While loading it shows the Kinetic [KineticBootIndicator]; when the work finishes the content
 * "powers on" by [clip-revealing][bootReveal] top-to-bottom rather than snapping in. This
 * composable also ensures the loading indicator is shown for at least the
 * [minimum duration specified in the application configuration][MINIMUM_LOADING_TIME_SETTING].
 *
 * @param suspendingFunction The suspending function to execute.
 * @param composable The composable content to display after loading.
 */
@Composable
fun LoadingWidget(suspendingFunction: suspend () -> Any?, composable: @Composable () -> Unit) {
  var isLoading by remember { mutableStateOf(true) }

  LaunchedEffect(Unit) {
    val minimumLoadingTime = MINIMUM_LOADING_TIME_SETTING.getValue()
    val timeTaken = measureTime { suspendingFunction() }
    if (timeTaken < minimumLoadingTime) {
      kotlinx.coroutines.delay(minimumLoadingTime - timeTaken)
    }
    isLoading = false
  }

  if (isLoading) {
    KineticBootIndicator(modifier = Modifier.fillMaxSize())
  } else {
    Box(modifier = Modifier.fillMaxSize().bootReveal()) { composable() }
  }
}

/**
 * Reveals content behind a hard horizontal edge that travels top-to-bottom once, on first
 * composition. The reveal fraction is held in an [Animatable] read inside [drawWithContent], so the
 * reveal runs in the draw phase with no recomposition of the wrapped content.
 */
private fun Modifier.bootReveal(): Modifier = composed {
  val reveal = remember { Animatable(0f) }
  LaunchedEffect(Unit) { reveal.animateTo(1f, animationSpec = KineticMotion.registerTween()) }
  drawWithContent {
    clipRect(bottom = (size.height * reveal.value).coerceIn(0f, size.height)) {
      this@drawWithContent.drawContent()
    }
  }
}
