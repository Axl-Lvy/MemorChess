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
 * @param route Logical route identifier, expected to match the active route passed to
 *   [KineticTopBar]. Typically a `Route` enum name.
 * @param label Uppercase nav label, e.g. `"EXPLORE"`.
 * @param number Small mono digit shown left of the label, e.g. `"01"`.
 */
data class KineticTopBarNavItem(val route: String, val label: String, val number: String)

/**
 * Kinetic top bar — mirrors `.topbar`, `.brand`, `.topnav`, `.top-meta` from
 * `design-proposals/kinetic-base.css`.
 *
 * Three-column layout: brand block (left), nav items (center), optional meta pills (right). A 1.dp
 * bottom border on `line` is laid down inside the row; a 1.dp gradient underline (transparent ->
 * cyan -> accent -> cyan -> transparent at alpha 0.5) is drawn on top via [drawWithContent] so it
 * appears flush with the bar's bottom edge.
 *
 * @param navItems Nav entries shown in the center strip.
 * @param activeRoute Route currently selected; matched against [KineticTopBarNavItem.route].
 * @param onNavigate Invoked with a nav item's route when the user taps it.
 * @param modifier Outer modifier.
 * @param versionLabel Optional mono caption under the brand name (e.g. `"0.0.1 · MULTIPLATFORM"`).
 *   Skipped when blank.
 * @param compact When true, uses a 44.dp bar height and a 28.dp brand mark — intended for mobile.
 * @param metaPills Optional right-side slot for [KineticTopBarPill]s (or any other content).
 */
@Composable
fun KineticTopBar(
  navItems: List<KineticTopBarNavItem>,
  activeRoute: String,
  onNavigate: (route: String) -> Unit,
  modifier: Modifier = Modifier,
  versionLabel: String = "",
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
          0.15f to palette.cyan,
          0.5f to palette.accent,
          0.85f to palette.cyan,
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
          // 1.dp bottom border on `line`
          val borderPx = 1.dp.toPx()
          drawRect(
            color = palette.line,
            topLeft = Offset(0f, size.height - borderPx),
            size = Size(size.width, borderPx),
          )
          // Gradient underline at alpha 0.5, flush with the bottom edge.
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
    // LEFT — brand block
    BrandBlock(markSize = markSize, versionLabel = versionLabel)

    // CENTER — nav items, fills remaining space, items left-aligned at start.
    Row(
      modifier = Modifier.weight(1f).fillMaxHeight().padding(start = 32.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(0.dp),
    ) {
      navItems.forEach { item ->
        // The back-stack route is the lower-case serial name; nav-item routes use the capitalised
        // label, so match case-insensitively.
        val active = item.route.equals(activeRoute, ignoreCase = true)
        TopNavLink(
          item = item,
          active = active,
          // No-op when already on this route — don't re-navigate to the current screen.
          onClick = { if (!active) onNavigate(item.route) },
        )
      }
    }

    // RIGHT — meta pills slot.
    Row(
      modifier = Modifier.fillMaxHeight(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.End,
      content = metaPills,
    )
  }
}

/** Brand block: skewed orange mark + "MEMOR/CHESS" wordmark with accent-colored slash. */
@Composable
private fun BrandBlock(markSize: androidx.compose.ui.unit.Dp, versionLabel: String) {
  val palette = LocalKineticPalette.current
  val typography = LocalKineticTypography.current

  Row(verticalAlignment = Alignment.CenterVertically) {
    BrandMark(size = markSize)
    Spacer(modifier = Modifier.width(12.dp))
    Column {
      val first = stringResource(Res.string.brand_wordmark_first)
      val second = stringResource(Res.string.brand_wordmark_second)
      val wordmark: AnnotatedString = buildAnnotatedString {
        withStyle(SpanStyle(color = palette.ink)) { append(first) }
        withStyle(SpanStyle(color = palette.accent, fontWeight = FontWeight.ExtraBold)) {
          append("/")
        }
        withStyle(SpanStyle(color = palette.ink)) { append(second) }
      }
      Text(text = wordmark, style = typography.brand)
      if (versionLabel.isNotEmpty()) {
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = versionLabel.uppercase(), style = typography.monoSm.copy(color = palette.ink3))
      }
    }
  }
}

/** Single center-nav link with active underline. */
@Composable
private fun TopNavLink(item: KineticTopBarNavItem, active: Boolean, onClick: () -> Unit) {
  val palette = LocalKineticPalette.current
  val typography = LocalKineticTypography.current
  val labelColor = if (active) palette.ink else palette.ink3
  val accent = palette.accent

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
            color = accent,
            topLeft = Offset(left, y),
            size = Size((right - left).coerceAtLeast(0f), strokePx),
          )
        }
      },
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(text = item.number, style = typography.monoSm.copy(color = palette.ink4, fontSize = 10.sp))
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
 * Meta pill rendered in the top bar's right-hand slot. Mirrors `.pill` / `.pill.hot` from
 * `design-proposals/kinetic-base.css`.
 *
 * Vertically stacks a small mono [label] over a Bricolage [value], with a 1.dp `line` left border
 * and 14.dp horizontal padding so consecutive pills sit flush against one another.
 *
 * @param label Small mono uppercase caption, e.g. `"EVAL"`.
 * @param value Big display value, e.g. `"+0.4"`.
 * @param hot When true, both label and value adopt `accentText`, and a leading `"● "` (in `accent`)
 *   is prepended to the value to signal a live data feed.
 */
@Composable
fun KineticTopBarPill(label: String, value: String, hot: Boolean = false) {
  val palette = LocalKineticPalette.current
  val typography = LocalKineticTypography.current

  val labelColor = if (hot) palette.accentText else palette.ink3
  val valueColor = if (hot) palette.accentText else palette.ink

  val valueText: AnnotatedString = buildAnnotatedString {
    if (hot) {
      withStyle(SpanStyle(color = palette.accent)) { append("● ") }
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
      Text(text = label.uppercase(), style = typography.monoSm.copy(color = labelColor))
      Spacer(modifier = Modifier.height(2.dp))
      Text(
        text = valueText,
        style =
          typography.brand.copy(fontWeight = FontWeight.Bold, fontSize = 17.sp, color = valueColor),
      )
    }
  }
}
