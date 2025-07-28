package proj.memorchess.axl.ui.layout.explore

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.ceil

@Composable
fun VerticalDoubleScrollableRow(
  modifier: Modifier = Modifier,
  children: List<@Composable () -> Unit>,
  separatorColor: Color = MaterialTheme.colorScheme.primary,
  minimumItemHeight: Dp = 64.dp,
) {
  val scrollState = rememberScrollState()
  BoxWithConstraints(modifier = modifier.fillMaxHeight().clip(RoundedCornerShape(8.dp))) {
    val itemCount = ceil(children.size.toDouble() / 2.0).toInt()
    val availableHeight = maxHeight
    val calculatedItemHeight = (availableHeight / itemCount).coerceAtLeast(minimumItemHeight)
    val totalRequiredHeight = minimumItemHeight * itemCount
    val isScrollable = totalRequiredHeight > availableHeight
    Column(
      modifier =
        if (isScrollable) Modifier.verticalScroll(scrollState) else Modifier.fillMaxHeight(),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      for (i in 0 until itemCount) {
        if (2 * i + 1 >= children.size) {
          Box(
            modifier =
              Modifier.height(if (isScrollable) minimumItemHeight else calculatedItemHeight)
          ) {
            children[2 * i]()
          }
        } else {
          Row(
            modifier =
              Modifier.fillMaxWidth()
                .height(if (isScrollable) minimumItemHeight else calculatedItemHeight),
            horizontalArrangement = Arrangement.Center,
          ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.weight(1f)) {
              children[2 * i]()
            }
            Box(Modifier.width(1.dp).fillMaxHeight().background(separatorColor))
            Box(contentAlignment = Alignment.Center, modifier = Modifier.weight(1f)) {
              children[2 * i + 1]()
            }
          }
        }
        if (2 * (i + 1) < children.size) {
          Box(Modifier.height(1.dp).fillMaxWidth().background(separatorColor))
        }
      }
    }
    DrawScrollIndicators(isScrollable, scrollState)
  }
}

@Composable
private fun BoxWithConstraintsScope.DrawScrollIndicators(
  isScrollable: Boolean,
  scrollState: ScrollState,
) {
  if (isScrollable) {
    // Left gradient
    if (scrollState.canScrollBackward) {
      Box(
        modifier =
          Modifier.align(Alignment.TopCenter)
            .fillMaxWidth()
            .height(32.dp)
            .background(
              Brush.verticalGradient(
                colors = listOf(Color.Black.copy(alpha = 0.30f), Color.Transparent)
              )
            )
      )
    }
    // Right gradient
    if (scrollState.canScrollForward) {
      Box(
        modifier =
          Modifier.align(Alignment.BottomCenter)
            .fillMaxWidth()
            .height(32.dp)
            .background(
              Brush.verticalGradient(
                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.30f))
              )
            )
      )
    }
  }
}
