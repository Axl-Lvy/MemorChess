package proj.memorchess.axl.ui.components.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import memorchess.composeapp.generated.resources.Res
import memorchess.composeapp.generated.resources.brand_wordmark_first
import memorchess.composeapp.generated.resources.brand_wordmark_second
import memorchess.composeapp.generated.resources.side_rail_day_streak
import memorchess.composeapp.generated.resources.side_rail_today_done
import memorchess.composeapp.generated.resources.side_rail_today_progress
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import proj.memorchess.axl.core.graph.TrainingScheduler
import proj.memorchess.axl.core.streak.StreakTracker
import proj.memorchess.axl.ui.components.brand.BrandMark
import proj.memorchess.axl.ui.theme.LocalKineticPalette
import proj.memorchess.axl.ui.theme.LocalKineticTypography

/** Rail width — the mockup's desktop `width: 232px`. */
private val SIDE_BAR_WIDTH = 232.dp

/** Thickness of the rail's right line (mockup: `border-right: 1.5px`). */
private val RAIL_BORDER = 1.5.dp

/** Vertical inset of the rail's content (mockup: `padding: 20px 14px`). */
private val RAIL_VERTICAL_PADDING = 20.dp

/** Horizontal inset of the rail's content. */
private val RAIL_HORIZONTAL_PADDING = 14.dp

/** Gap between the rail's stacked blocks and between nav rows (mockup: `gap: 8px`). */
private val RAIL_GAP = 8.dp

/** Brand tile size at the top of the rail. */
private val BRAND_TILE = 34.dp

/** Corner radius of a nav row, matching the bottom bar's active pill. */
private val ROW_SHAPE = RoundedCornerShape(14.dp)

/** Vertical padding inside a nav row (mockup: `padding: 11px 12px`). */
private val ROW_VERTICAL_PADDING = 11.dp

/** Horizontal padding inside a nav row. */
private val ROW_HORIZONTAL_PADDING = 12.dp

/** Gap between a row's icon, label and badge (mockup: `gap: 11px`). */
private val ROW_GAP = 11.dp

/** Icon size inside a rail row (mockup: `width: 21px`). */
private val RAIL_ICON_SIZE = 21.dp

/** Corner radius of the row's count badge (mockup: `border-radius: 8px`). */
private val BADGE_SHAPE = RoundedCornerShape(8.dp)

/** Offset of the streak card's hard bottom edge — the mockup's `box-shadow: 0 3px 0`. */
private val STREAK_EDGE_OFFSET = 3.dp

/** Fixed gap between the streak card and the first nav row (mockup: a 6px spacer). */
private val STREAK_NAV_SPACER = 6.dp

/** Snapshot of the numbers shown on the rail; `null` until the first fetch resolves. */
@Immutable
private data class RailStats(val streak: Int, val done: Int, val target: Int, val due: Int)

/**
 * Kinetic vertical chrome rail. Used on compact-height screens — phone landscape, or a desktop
 * window shortened below the medium-height breakpoint — where a horizontal top bar would eat too
 * much vertical space. A desktop window at a normal height still gets the top bar, not this rail.
 *
 * Owns the chrome — `panel` background, 1.5.dp `line` right border, system-bar insets and padding —
 * and stacks a brand row, a streak card and a [KineticSideNav] block of full-width labelled rows.
 * The outer edge stays square: it is edge-anchored. The content scrolls, because the rail's natural
 * height only just fits a phone in landscape and a larger font scale overflows it.
 *
 * **Where the streak target comes from.** There is no daily-goal setting in the app, so the target
 * is `StreakTracker.cardsCompletedToday() + TrainingScheduler.dueCount()` — what has been done
 * today plus the due reviews and due new cards still to come today, once the daily caps are
 * applied. [TrainingScheduler.pendingCount] is deliberately **not** used here: it also folds in
 * every in-session (mid learning) card, and a card graded today already counts in `done`, so adding
 * it a second time via `pendingCount()` made the target grow when a review failed and shrink as the
 * card graduated. The Training row's badge still uses `pendingCount()`, since that badge means
 * "everything left to serve", in-session cards included. When the total is `0` the `/target` half
 * is dropped rather than a number being invented.
 *
 * **Refresh contract.** Refreshed on navigation ([currentRoute] is a `produceState` key), not live:
 * neither [StreakTracker] nor [TrainingScheduler] emits a stream today, so counts advanced during
 * an open training session are picked up the next time the user changes screen.
 *
 * @param items Route entries to render as nav rows.
 * @param currentRoute Active route label (matched against `item.destination.getLabel()`).
 * @param onSelect Invoked with the tapped item.
 * @param modifier Outer modifier.
 * @param itemModifier Optional per-row modifier, e.g. for attaching `Modifier.testTag(...)`.
 * @param streakTracker Source of the streak and today's completed count.
 * @param scheduler Source of the still-due count driving the target and the Training badge.
 */
@Composable
fun KineticSideBar(
  items: List<NavigationBarItemContent>,
  currentRoute: String,
  onSelect: (NavigationBarItemContent) -> Unit,
  modifier: Modifier = Modifier,
  itemModifier: (NavigationBarItemContent) -> Modifier = { Modifier },
  streakTracker: StreakTracker = koinInject(),
  scheduler: TrainingScheduler = koinInject(),
) {
  val palette = LocalKineticPalette.current
  val stats by
    produceState<RailStats?>(null, streakTracker, scheduler, currentRoute) {
      val done = streakTracker.cardsCompletedToday()
      val due = scheduler.pendingCount()
      val target = done + scheduler.dueCount()
      value =
        RailStats(streak = streakTracker.streakDays(), done = done, target = target, due = due)
    }
  Column(
    modifier =
      modifier
        .width(SIDE_BAR_WIDTH)
        .fillMaxHeight()
        .background(palette.panel)
        .drawWithContent {
          drawContent()
          val borderPx = RAIL_BORDER.toPx()
          drawRect(
            color = palette.line,
            topLeft = Offset(size.width - borderPx, 0f),
            size = Size(borderPx, size.height),
          )
        }
        .windowInsetsPadding(WindowInsets.systemBars)
        .verticalScroll(rememberScrollState())
        .padding(vertical = RAIL_VERTICAL_PADDING, horizontal = RAIL_HORIZONTAL_PADDING),
    verticalArrangement = Arrangement.spacedBy(RAIL_GAP),
  ) {
    RailBrandRow()
    RailStreakCard(stats)
    Spacer(modifier = Modifier.height(STREAK_NAV_SPACER))
    KineticSideNav(
      items = items,
      currentRoute = currentRoute,
      onSelect = onSelect,
      itemModifier = itemModifier,
      badgeCount = { item -> if (item == NavigationBarItemContent.Training) stats?.due else null },
    )
  }
}

/**
 * Brand tile plus wordmark at the top of the rail.
 *
 * [BrandMark] is reused unchanged: it draws a skewed parallelogram rather than the mockup's 11.dp
 * rounded tile, and restyling it belongs to a brand/top-bar issue. The wordmark reuses the top
 * bar's "MEMOR/CHESS" split rather than the mockup's Title-Case "MemorChess" — same casing call as
 * the nav labels.
 */
@Composable
private fun RailBrandRow() {
  val palette = LocalKineticPalette.current
  val typography = LocalKineticTypography.current
  val first = stringResource(Res.string.brand_wordmark_first)
  val second = stringResource(Res.string.brand_wordmark_second)
  val wordmark: AnnotatedString = buildAnnotatedString {
    withStyle(SpanStyle(color = palette.ink)) { append(first) }
    withStyle(SpanStyle(color = palette.action, fontWeight = FontWeight.ExtraBold)) { append("/") }
    withStyle(SpanStyle(color = palette.ink)) { append(second) }
  }
  Row(
    modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, bottom = 14.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    BrandMark(size = BRAND_TILE)
    Text(
      text = wordmark,
      style = typography.brand.copy(fontSize = 18.sp, fontWeight = FontWeight.ExtraBold),
      // The row leaves the wordmark ~144.dp; at a large system font scale it would otherwise wrap
      // to two lines and push the streak card down. Clip instead of ellipsis: the brand mark beside
      // it already identifies the app, so a trailing "…" would only add noise.
      maxLines = 1,
      overflow = TextOverflow.Clip,
    )
  }
}

/**
 * Streak card: the day count beside a "DAY STREAK" label over today's progress.
 *
 * Renders with both number slots empty while [stats] is still `null`, so the card never flashes a
 * "0" it is about to replace. The whole card inks with `onStreak` in both themes.
 *
 * @param stats Resolved rail numbers, or `null` while the first fetch is in flight.
 */
@Composable
private fun RailStreakCard(stats: RailStats?) {
  val palette = LocalKineticPalette.current
  val typography = LocalKineticTypography.current
  val shape = MaterialTheme.shapes.small
  val todayLine =
    when {
      stats == null -> ""
      stats.target > 0 ->
        stringResource(Res.string.side_rail_today_progress, stats.done, stats.target)
      else -> stringResource(Res.string.side_rail_today_done, stats.done)
    }
  Row(
    modifier =
      Modifier.fillMaxWidth()
        .drawBehind {
          translate(top = STREAK_EDGE_OFFSET.toPx()) {
            drawOutline(
              outline = shape.createOutline(size, layoutDirection, this),
              color = palette.streakEdge,
            )
          }
        }
        .background(palette.streak, shape)
        .clip(shape)
        .padding(vertical = 12.dp, horizontal = 14.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Text(
      text = stats?.streak?.toString() ?: "",
      style = typography.displayLg.copy(fontSize = 26.sp, color = palette.onStreak),
    )
    Column {
      Text(
        // 0 while stats are still loading: the plural form a real streak of 0 would use.
        text = pluralStringResource(Res.plurals.side_rail_day_streak, stats?.streak ?: 0),
        // labelSm already carries the mockup's 9sp size and 0.1em tracking.
        style = typography.labelSm.copy(fontWeight = FontWeight.Black, color = palette.onStreak),
      )
      Text(
        text = todayLine,
        style =
          typography.bodySm.copy(
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Bold,
            color = palette.onStreak,
          ),
      )
    }
  }
}

/**
 * Kinetic wide-screen side navigation: full-width labelled rows.
 *
 * Each row is an icon, an uppercase label and an optional count badge. The active row fills with
 * `actionDim` at a 14.dp radius and inks with `action`; inactive rows paint nothing (their style
 * colour is `actionDim` at alpha 0) and ink with `ink3`. Unlike the bottom bar's pill the fill is
 * **not** animated — a full-row fill is not an appearing pill, and keeping it static leaves the
 * rail with no animation cost and no reduce-motion settings read.
 *
 * @param items Navigation entries; rendered in the order supplied.
 * @param currentRoute The current route label; compared against [NavigationBarItemContent]'s
 *   destination label to determine the active row.
 * @param onSelect Invoked when the user taps a row.
 * @param modifier Optional layout modifier.
 * @param itemModifier Optional per-row modifier, e.g. for attaching `Modifier.testTag(...)`.
 * @param badgeCount Optional trailing count per item; `null` or a non-positive value renders no
 *   badge.
 */
@Composable
fun KineticSideNav(
  items: List<NavigationBarItemContent>,
  currentRoute: String,
  onSelect: (NavigationBarItemContent) -> Unit,
  modifier: Modifier = Modifier,
  itemModifier: (NavigationBarItemContent) -> Modifier = { Modifier },
  badgeCount: (NavigationBarItemContent) -> Int? = { null },
) {
  val palette = LocalKineticPalette.current
  val typography = LocalKineticTypography.current
  Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(RAIL_GAP)) {
    items.forEach { item ->
      val active = isActive(item, currentRoute)
      val style = kineticNavCellStyle(palette, active)
      Row(
        modifier =
          Modifier.fillMaxWidth()
            // Clip first so the ripple respects the row's corners.
            .clip(ROW_SHAPE)
            .then(itemModifier(item))
            .semantics { selected = active }
            // Tapping the row for the screen you are already on is a no-op; the node stays
            // clickable so its test tag is tappable.
            .clickable(enabled = true, onClick = { if (!active) onSelect(item) })
            .background(style.pill, ROW_SHAPE)
            .padding(vertical = ROW_VERTICAL_PADDING, horizontal = ROW_HORIZONTAL_PADDING),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ROW_GAP),
      ) {
        NavCellIcon(item, style.content, size = RAIL_ICON_SIZE)
        Text(
          // Kept uppercase for the same reason as the bottom bar's labels.
          text = stringResource(item.destination.displayNameRes()).uppercase(),
          style =
            typography.label.copy(
              fontSize = 13.5.sp,
              fontWeight = style.labelWeight,
              letterSpacing = 0.em,
              color = style.content,
            ),
          modifier = Modifier.weight(1f),
        )
        badgeCount(item)
          ?.takeIf { it > 0 }
          ?.let { count ->
            Box(
              modifier =
                Modifier.background(palette.action, BADGE_SHAPE)
                  .padding(vertical = 2.dp, horizontal = 7.dp)
            ) {
              Text(
                text = count.toString(),
                style =
                  typography.labelSm.copy(
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.em,
                    color = palette.onAction,
                  ),
              )
            }
          }
      }
    }
  }
}
