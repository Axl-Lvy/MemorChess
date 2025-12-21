package proj.memorchess.axl.ui.components.buttons

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A horizontally scrollable row of selectable items, each represented by a [WideScrollBarChild].
 *
 * @param modifier Modifier to be applied to the row.
 * @param children List of [WideScrollBarChild] items to display.
 * @param minWidth Minimum width for each child item.
 * @param changeSelectedColor If true, changes the color of the selected item.
 * @param selectedInitial The index of the initially selected item, or -1 for none.
 * @param baseColor The background color for unselected items.
 * @param selectedColor The background color for the selected item.
 */
@Composable
fun WideScrollableRow(
  modifier: Modifier = Modifier,
  children: List<WideScrollBarChild>,
  minWidth: Dp = 16.dp,
  changeSelectedColor: Boolean = true,
  selectedInitial: Int = -1,
  baseColor: Color = MaterialTheme.colorScheme.primaryContainer,
  selectedColor: Color = MaterialTheme.colorScheme.primary,
) {
  var selected by remember { mutableStateOf(selectedInitial) }
  val scrollState = rememberScrollState()
  val colors = WideScrollableRowColors(baseColor, selectedColor, changeSelectedColor)

  BoxWithConstraints(modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))) {
    WideScrollableRowContent(
      children = children,
      minWidth = minWidth,
      selected = selected,
      onSelect = { selected = it },
      colors = colors,
      scrollState = scrollState,
    )
  }
}

@Composable
private fun BoxWithConstraintsScope.WideScrollableRowContent(
  children: List<WideScrollBarChild>,
  minWidth: Dp,
  selected: Int,
  onSelect: (Int) -> Unit,
  colors: WideScrollableRowColors,
  scrollState: ScrollState,
) {
  val isScrollable = (minWidth * children.size) > maxWidth
  val itemWidth = if (isScrollable) minWidth else (maxWidth / children.size).coerceAtLeast(minWidth)

  Row(
    modifier =
      if (isScrollable) Modifier.horizontalScroll(scrollState) else Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    children.forEachIndexed { i, child ->
      WideScrollableRowItem(
        child = child,
        isSelected = i == selected,
        width = itemWidth,
        colors = colors,
        onClick = {
          if (colors.changeSelected) {
            onSelect(i)
          }
          child.onClick()
        },
      )
      if (i < children.lastIndex) {
        VerticalSeparator(colors.selected)
      }
    }
  }
  DrawScrollIndicators(isScrollable, scrollState)
}

@Composable
private fun VerticalSeparator(color: Color) {
  Box(Modifier.width(1.dp).fillMaxHeight().background(color))
}

@Composable
private fun WideScrollableRowItem(
  child: WideScrollBarChild,
  isSelected: Boolean,
  width: Dp,
  colors: WideScrollableRowColors,
  onClick: () -> Unit,
) {
  Box(
    modifier =
      Modifier.width(width)
        .background(
          if (isSelected && colors.changeSelected) {
            colors.selected
          } else {
            colors.base
          }
        )
        .testTag(child.testTag)
        .clickable(!isSelected) { onClick() }
        .fillMaxHeight(),
    contentAlignment = Alignment.Center,
  ) {
    child.drawContent(isSelected)
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
            .width(24.dp)
            .background(
              Brush.horizontalGradient(
                colors = listOf(Color.Black.copy(alpha = 0.15f), Color.Transparent)
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
            .width(24.dp)
            .background(
              Brush.horizontalGradient(
                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.15f))
              )
            )
      )
    }
  }
}

private data class WideScrollableRowColors(
  val base: Color,
  val selected: Color,
  val changeSelected: Boolean,
)
