package proj.memorchess.axl.ui.layout.explore

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun HorizontalScrollableRow(
  modifier: Modifier = Modifier,
  children: List<@Composable () -> Unit>,
  separatorColor: Color = MaterialTheme.colorScheme.primary,
  minimumItemWidth: Dp = 64.dp,
) {
  val scrollState = rememberScrollState()
  BoxWithConstraints(modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))) {
    val itemCount = children.size
    val availableWidth = maxWidth
    val calculatedItemWidth = (availableWidth / itemCount).coerceAtLeast(minimumItemWidth)
    val totalRequiredWidth = minimumItemWidth * itemCount
    val isScrollable = totalRequiredWidth > availableWidth
    Row(
      modifier =
        if (isScrollable) Modifier.horizontalScroll(scrollState) else Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      children.forEachIndexed { i, child ->
        Box(
          modifier = Modifier.width(if (isScrollable) minimumItemWidth else calculatedItemWidth)
        ) {
          child()
        }
        if (i < children.lastIndex) {
          Box(Modifier.width(1.dp).fillMaxHeight().background(separatorColor))
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
          Modifier.align(Alignment.CenterStart)
            .fillMaxHeight()
            .width(32.dp)
            .background(
              Brush.horizontalGradient(
                colors = listOf(Color.Black.copy(alpha = 0.30f), Color.Transparent)
              )
            )
      )
    }
    // Right gradient
    if (scrollState.canScrollForward) {
      Box(
        modifier =
          Modifier.align(Alignment.CenterEnd)
            .fillMaxHeight()
            .width(32.dp)
            .background(
              Brush.horizontalGradient(
                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.30f))
              )
            )
      )
    }
  }
}
