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
import kotlinx.datetime.isoDayNumber
import memorchess.composeapp.generated.resources.Res
import memorchess.composeapp.generated.resources.brand_wordmark_first
import memorchess.composeapp.generated.resources.brand_wordmark_second
import memorchess.composeapp.generated.resources.side_rail_day_streak
import memorchess.composeapp.generated.resources.side_rail_today_done
import memorchess.composeapp.generated.resources.side_rail_today_progress
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import proj.memorchess.axl.core.date.DateUtil
import proj.memorchess.axl.core.graph.TrainingScheduler
import proj.memorchess.axl.core.streak.StreakTracker
import proj.memorchess.axl.ui.components.brand.BrandMark
import proj.memorchess.axl.ui.components.today.WeekStrip
import proj.memorchess.axl.ui.theme.LocalKineticPalette
import proj.memorchess.axl.ui.theme.LocalKineticTypography

private val SIDE_BAR_WIDTH = 232.dp

private val RAIL_BORDER = 1.5.dp

private val RAIL_VERTICAL_PADDING = 20.dp

private val RAIL_HORIZONTAL_PADDING = 14.dp

private val RAIL_GAP = 8.dp

private val BRAND_TILE = 34.dp

private val ROW_SHAPE = RoundedCornerShape(14.dp)

private val ROW_VERTICAL_PADDING = 11.dp

private val ROW_HORIZONTAL_PADDING = 12.dp

private val ROW_GAP = 11.dp

private val RAIL_ICON_SIZE = 21.dp

private val BADGE_SHAPE = RoundedCornerShape(8.dp)

private val STREAK_EDGE_OFFSET = 3.dp

private val STREAK_NAV_SPACER = 6.dp

@Immutable
private data class RailStats(val streak: Int, val done: Int, val target: Int, val due: Int)

/**
 * Vertical chrome rail for compact-height screens, stacking the brand, a streak card and a
 * [KineticSideNav]. Its counts refresh on navigation, not live.
 *
 * @param currentRoute Active route label, matched against `item.destination.getLabel()`.
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
      // dueCount, not pendingCount: in-session cards graded today already count in done.
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
    StreakCard(streak = stats?.streak, done = stats?.done ?: 0, target = stats?.target ?: 0)
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
      // A large font scale would wrap the wordmark and push the streak card down. Clip rather than
      // ellipsize: the brand mark beside it already identifies the app.
      maxLines = 1,
      overflow = TextOverflow.Clip,
    )
  }
}

/**
 * Streak card showing the day count over today's progress. While [streak] is `null` both number
 * slots stay empty and [done] and [target] are ignored, so the card never flashes a placeholder
 * "0".
 */
@Composable
internal fun StreakCard(streak: Int?, done: Int, target: Int) {
  val palette = LocalKineticPalette.current
  val typography = LocalKineticTypography.current
  val shape = MaterialTheme.shapes.small
  val todayLine =
    when {
      streak == null -> ""
      target > 0 -> stringResource(Res.string.side_rail_today_progress, done, target)
      else -> stringResource(Res.string.side_rail_today_done, done)
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
      text = streak?.toString() ?: "",
      style = typography.displayLg.copy(fontSize = 26.sp, color = palette.onStreak),
    )
    Column {
      Text(
        // 0 while stats are still loading: the plural form a real streak of 0 would use.
        text = pluralStringResource(Res.plurals.side_rail_day_streak, streak ?: 0),
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
 * Wide-screen side navigation of full-width labelled rows. Unlike the bottom bar, nothing animates,
 * so it reads no reduce-motion setting.
 *
 * @param currentRoute Current route label, compared against each item's destination label.
 * @param badgeCount Trailing count per item; `null` or a non-positive value renders no badge.
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
            // Stays clickable when active, as a no-op, so its test tag remains tappable.
            .clickable(enabled = true, onClick = { if (!active) onSelect(item) })
            .background(style.pill, ROW_SHAPE)
            .padding(vertical = ROW_VERTICAL_PADDING, horizontal = ROW_HORIZONTAL_PADDING),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ROW_GAP),
      ) {
        // Rail icons never pop, even on the active row.
        NavCellIcon(item, style.content, size = RAIL_ICON_SIZE, active = false)
        Text(
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

@Immutable
private data class DesktopRailStats(
  val streak: Int,
  val done: Int,
  val target: Int,
  val due: Int,
  val week: List<Boolean>,
  val todayIsoIndex: Int,
)

/**
 * Desktop rail for wide and tall windows: the [KineticSideBar] blocks in a scrollable section, with
 * a [WeekStrip] anchored at the bottom. Its counts refresh on navigation, not live.
 *
 * @param currentRoute Active route label, matched against `item.destination.getLabel()`.
 */
@Composable
fun KineticDesktopRail(
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
    produceState<DesktopRailStats?>(null, streakTracker, scheduler, currentRoute) {
      val done = streakTracker.cardsCompletedToday()
      val due = scheduler.pendingCount()
      // dueCount, not pendingCount: in-session cards graded today already count in done.
      val target = done + scheduler.dueCount()
      value =
        DesktopRailStats(
          streak = streakTracker.streakDays(),
          done = done,
          target = target,
          due = due,
          week = streakTracker.weekActivity(),
          todayIsoIndex = DateUtil.today().dayOfWeek.isoDayNumber,
        )
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
        .padding(vertical = RAIL_VERTICAL_PADDING, horizontal = RAIL_HORIZONTAL_PADDING)
  ) {
    Column(
      modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
      verticalArrangement = Arrangement.spacedBy(RAIL_GAP),
    ) {
      RailBrandRow()
      StreakCard(streak = stats?.streak, done = stats?.done ?: 0, target = stats?.target ?: 0)
      Spacer(modifier = Modifier.height(STREAK_NAV_SPACER))
      KineticSideNav(
        items = items,
        currentRoute = currentRoute,
        onSelect = onSelect,
        itemModifier = itemModifier,
        badgeCount = { item ->
          if (item == NavigationBarItemContent.Training) stats?.due else null
        },
      )
    }
    stats?.let {
      Spacer(modifier = Modifier.height(RAIL_GAP))
      WeekStrip(
        week = it.week,
        todayIsoIndex = it.todayIsoIndex,
        tagPrefix = "rail_week",
        // The default 40.dp cells don't fit seven across the rail; 7 * 21.dp + 6 * 8.dp gaps do.
        cellSize = RAIL_ICON_SIZE,
      )
    }
  }
}
