package proj.memorchess.axl.ui.components.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import memorchess.composeapp.generated.resources.Res
import memorchess.composeapp.generated.resources.brand_wordmark_first
import memorchess.composeapp.generated.resources.brand_wordmark_second
import org.jetbrains.compose.resources.stringResource
import proj.memorchess.axl.ui.components.brand.BrandMark
import proj.memorchess.axl.ui.theme.LocalKineticPalette
import proj.memorchess.axl.ui.theme.LocalKineticTypography

/**
 * One entry in the [KineticTopBar] center navigation strip.
 *
 * @param number Small digit shown left of the label, e.g. `"01"`.
 * @param ownedRoutes Every route this entry is highlighted for. Defaults to just [route].
 */
data class KineticTopBarNavItem(
  val route: String,
  val label: String,
  val number: String,
  val ownedRoutes: Set<String> = setOf(route),
)

/**
 * Top bar with the brand block, a center navigation strip and a right-side [metaPills] slot.
 *
 * @param activeRoute Route currently selected, matched case-insensitively against
 *   [KineticTopBarNavItem.ownedRoutes].
 * @param compact Whether to use the shorter mobile bar.
 */
@Composable
fun KineticTopBar(
  navItems: List<KineticTopBarNavItem>,
  activeRoute: String,
  onNavigate: (route: String) -> Unit,
  modifier: Modifier = Modifier,
  compact: Boolean = false,
  metaPills: @Composable RowScope.() -> Unit = {},
) {
  val palette = LocalKineticPalette.current
  val barHeight = if (compact) 44.dp else 64.dp
  val markSize = if (compact) 28.dp else 36.dp

  val gradientBrush =
    Brush.horizontalGradient(
      colorStops =
        arrayOf(
          0.0f to Color.Transparent,
          0.15f to palette.streak,
          0.5f to palette.action,
          0.85f to palette.streak,
          1.0f to Color.Transparent,
        )
    )

  Row(
    modifier =
      modifier
        .fillMaxWidth()
        .background(palette.bg2)
        .windowInsetsPadding(WindowInsets.statusBars)
        .height(barHeight)
        .drawWithContent {
          drawContent()
          val borderPx = 1.dp.toPx()
          drawRect(
            color = palette.line,
            topLeft = Offset(0f, size.height - borderPx),
            size = Size(size.width, borderPx),
          )
          drawRect(
            brush = gradientBrush,
            topLeft = Offset(0f, size.height - borderPx),
            size = Size(size.width, borderPx),
            alpha = 0.5f,
          )
        }
        .padding(horizontal = 24.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    BrandBlock(markSize = markSize)

    Row(
      modifier = Modifier.weight(1f).fillMaxHeight().padding(start = 32.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(0.dp),
    ) {
      navItems.forEach { item ->
        // Back stack routes are lower case serial names; nav routes are capitalised labels.
        val active = item.ownedRoutes.any { it.equals(activeRoute, ignoreCase = true) }
        TopNavLink(
          item = item,
          active = active,
          onClick = { if (!active) onNavigate(item.route) },
        )
      }
    }

    Row(
      modifier = Modifier.fillMaxHeight(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.End,
      content = metaPills,
    )
  }
}

@Composable
private fun BrandBlock(markSize: androidx.compose.ui.unit.Dp) {
  val palette = LocalKineticPalette.current
  val typography = LocalKineticTypography.current

  Row(verticalAlignment = Alignment.CenterVertically) {
    BrandMark(size = markSize)
    Spacer(modifier = Modifier.width(12.dp))
    val first = stringResource(Res.string.brand_wordmark_first)
    val second = stringResource(Res.string.brand_wordmark_second)
    val wordmark: AnnotatedString = buildAnnotatedString {
      withStyle(SpanStyle(color = palette.ink)) { append(first) }
      withStyle(SpanStyle(color = palette.action, fontWeight = FontWeight.ExtraBold)) {
        append("/")
      }
      withStyle(SpanStyle(color = palette.ink)) { append(second) }
    }
    Text(text = wordmark, style = typography.brand)
  }
}

@Composable
private fun TopNavLink(item: KineticTopBarNavItem, active: Boolean, onClick: () -> Unit) {
  val palette = LocalKineticPalette.current
  val typography = LocalKineticTypography.current
  val labelColor = if (active) palette.ink else palette.ink3
  val action = palette.action

  Row(
    modifier =
      Modifier.fillMaxHeight().clickable(onClick = onClick).padding(horizontal = 16.dp).drawBehind {
        if (active) {
          val strokePx = 2.dp.toPx()
          val insetPx = 16.dp.toPx()
          val left = insetPx
          val right = size.width - insetPx
          val bottomInsetPx = 14.dp.toPx()
          val y = size.height - bottomInsetPx
          drawRect(
            color = action,
            topLeft = Offset(left, y),
            size = Size((right - left).coerceAtLeast(0f), strokePx),
          )
        }
      },
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      text = item.number,
      style = typography.labelSm.copy(color = palette.ink4, fontSize = 10.sp),
    )
    Spacer(modifier = Modifier.width(8.dp))
    Text(
      text = item.label,
      style =
        typography.body.copy(
          fontFamily = typography.brand.fontFamily,
          fontWeight = FontWeight.Medium,
          fontSize = 13.5.sp,
          color = labelColor,
        ),
    )
  }
}

/**
 * Caption-over-value pill for the top bar's right-hand slot.
 *
 * @param hot Whether to highlight the pill as a live data feed.
 */
@Composable
fun KineticTopBarPill(label: String, value: String, hot: Boolean = false) {
  val palette = LocalKineticPalette.current
  val typography = LocalKineticTypography.current

  val labelColor = if (hot) palette.actionText else palette.ink3
  val valueColor = if (hot) palette.actionText else palette.ink

  val valueText: AnnotatedString = buildAnnotatedString {
    if (hot) {
      withStyle(SpanStyle(color = palette.action)) { append("● ") }
    }
    append(value)
  }

  Box(
    modifier =
      Modifier.fillMaxHeight()
        .drawBehind {
          val strokePx = 1.dp.toPx()
          drawRect(
            color = palette.line,
            topLeft = Offset(0f, 0f),
            size = Size(strokePx, size.height),
          )
        }
        .padding(PaddingValues(horizontal = 14.dp)),
    contentAlignment = Alignment.CenterStart,
  ) {
    Column(verticalArrangement = Arrangement.Center) {
      Text(text = label.uppercase(), style = typography.labelSm.copy(color = labelColor))
      Spacer(modifier = Modifier.height(2.dp))
      Text(
        text = valueText,
        style =
          typography.brand.copy(fontWeight = FontWeight.Bold, fontSize = 17.sp, color = valueColor),
      )
    }
  }
}
