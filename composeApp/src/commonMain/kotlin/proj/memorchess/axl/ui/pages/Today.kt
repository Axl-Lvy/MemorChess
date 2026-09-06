package proj.memorchess.axl.ui.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.datetime.isoDayNumber
import memorchess.composeapp.generated.resources.Res
import memorchess.composeapp.generated.resources.today_goal_done
import memorchess.composeapp.generated.resources.today_goal_progress
import memorchess.composeapp.generated.resources.today_greeting
import memorchess.composeapp.generated.resources.today_pickup_empty
import memorchess.composeapp.generated.resources.today_pickup_progress
import memorchess.composeapp.generated.resources.today_pickup_title
import memorchess.composeapp.generated.resources.today_start_review_cta
import memorchess.composeapp.generated.resources.today_streak_label
import memorchess.composeapp.generated.resources.today_weekday_friday
import memorchess.composeapp.generated.resources.today_weekday_monday
import memorchess.composeapp.generated.resources.today_weekday_saturday
import memorchess.composeapp.generated.resources.today_weekday_sunday
import memorchess.composeapp.generated.resources.today_weekday_thursday
import memorchess.composeapp.generated.resources.today_weekday_tuesday
import memorchess.composeapp.generated.resources.today_weekday_wednesday
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import proj.memorchess.axl.core.data.repertoire.RepertoireMastery
import proj.memorchess.axl.core.data.repertoire.mostRecentRepertoireMastery
import proj.memorchess.axl.core.date.DateUtil
import proj.memorchess.axl.core.graph.TrainingScheduler
import proj.memorchess.axl.core.graph.TreeStore
import proj.memorchess.axl.core.streak.StreakTracker
import proj.memorchess.axl.ui.components.buttons.KineticButton
import proj.memorchess.axl.ui.components.buttons.KineticButtonStyle
import proj.memorchess.axl.ui.components.today.GoalRing
import proj.memorchess.axl.ui.pages.navigation.LocalNavigator
import proj.memorchess.axl.ui.pages.navigation.Route
import proj.memorchess.axl.ui.theme.LocalKineticPalette
import proj.memorchess.axl.ui.theme.LocalKineticTypography

// Every dimension below is a placeholder, not an artboard read: screens `1a`/`1g`/`1m` of the
// design canvas (`claude.ai/design/p/db4f236e-b602-4b5f-bcbb-a4cf70525664`) could not be reached
// from this sandboxed environment (no authenticated browser session, no design-system project
// access for this file — every access path attempted is listed in the commit body). Each constant
// below instead cites the closest existing Kinetic value it reuses, per the spec's own fallback
// rule. All of them need a pass against the real artboard before merge.

/** Size of one week-strip day cell. Not read off an artboard; see the file header note. */
private val WEEK_CELL_SIZE = 40.dp

/**
 * Gap between week-strip cells, reused from
 * [proj.memorchess.axl.ui.components.navigation.KineticSideBar]'s row gap (8.dp).
 */
private val WEEK_CELL_GAP = 8.dp

/**
 * Corner radius of a week-strip cell, reused from the bottom nav's active-pill radius (14.dp),
 * halved for a smaller element.
 */
private val WEEK_CELL_SHAPE = RoundedCornerShape(10.dp)

/**
 * Visual state of one week-strip cell.
 *
 * Proposed as the 4-value shape pending sign-off against the mockup (see the commit body): a
 * distinct [MISSED] arm for a past day with no review, and [TODAY] always winning over [DONE] even
 * once today has been reviewed — today's own done-ness is communicated by the streak badge and the
 * goal ring instead, so the strip does not double-encode it. If a future design review finds the
 * mockup shows only three states, [MISSED] collapses into the same unfilled treatment as [FUTURE]
 * for past days.
 */
internal enum class WeekCellState {
  DONE,
  MISSED,
  TODAY,
  FUTURE,
}

/**
 * Classifies one week-strip cell at [isoIndex] (`1` Monday .. `7` Sunday), given [todayIsoIndex]
 * and whether that date counts towards the streak ([active]).
 *
 * A pure function so every arm gets its own direct assertion regardless of which real calendar day
 * a test happens to run on — [DateUtil.today] has no test override, so a full four-arm sample is
 * only ever available at test time by picking `todayIsoIndex` explicitly rather than depending on
 * the day the suite runs.
 */
internal fun classifyWeekCell(isoIndex: Int, todayIsoIndex: Int, active: Boolean): WeekCellState =
  when {
    isoIndex == todayIsoIndex -> WeekCellState.TODAY
    isoIndex > todayIsoIndex -> WeekCellState.FUTURE
    active -> WeekCellState.DONE
    else -> WeekCellState.MISSED
  }

/** Snapshot of the numbers the Today page renders; `null` until the first fetch resolves. */
private data class TodayStats(
  val streak: Int,
  val done: Int,
  val target: Int,
  val pendingCount: Int,
  val week: List<Boolean>,
)

/**
 * Resolution state of the "pick up where you left off" card.
 *
 * [mostRecentRepertoireMastery] is a legitimately nullable domain value (`null` means either zero
 * registered repertoires, or repertoires registered but none ever reviewed), so a bare
 * `RepertoireMastery?` in a `produceState` cannot tell "still loading" apart from "loaded, nothing
 * to show" — that would flash the empty-state copy before the query resolves.
 */
private sealed interface PickUpCardState {
  data object Loading : PickUpCardState

  data class Ready(val mastery: RepertoireMastery?) : PickUpCardState
}

/** The localized weekday name for ISO day number [isoDayNumber] (`1` Monday .. `7` Sunday). */
private fun weekdayNameRes(isoDayNumber: Int): StringResource =
  when (isoDayNumber) {
    1 -> Res.string.today_weekday_monday
    2 -> Res.string.today_weekday_tuesday
    3 -> Res.string.today_weekday_wednesday
    4 -> Res.string.today_weekday_thursday
    5 -> Res.string.today_weekday_friday
    6 -> Res.string.today_weekday_saturday
    else -> Res.string.today_weekday_sunday
  }

/**
 * The Today landing page: the dashboard the bottom nav's Training tab now opens onto (see
 * [Route.TodayRoute]). Shows the current streak, this ISO week's activity, today's goal ring, a
 * "Start review" CTA that pushes the real training board ([Route.TrainingRoute]), and a "pick up
 * where you left off" card for the most recently trained repertoire.
 *
 * Stats (streak, done, target, pending count, week activity) are computed once per composition in a
 * `produceState` block, mirroring `KineticSideBar`'s `RailStats`; nothing renders until that first
 * resolves, so the page never flashes a `"0"` or an empty ring it is about to replace. The pick-up
 * card resolves separately, through [PickUpCardState], for the same reason.
 *
 * Returning from the pushed [Route.TrainingRoute] board re-triggers both `produceState` blocks
 * naturally: `NavHost` disposes and recomposes this entry on pop, so a just-finished session's
 * "done" count and pick-up progress are picked up with no explicit refresh key.
 *
 * @param streakTracker Source of the streak, today's completed count, and this week's activity.
 * @param scheduler Source of the still-due count (the goal target) and the pending count (the CTA).
 * @param treeStore Source of the most recently trained repertoire's mastery snapshot.
 */
@Composable
fun Today(
  streakTracker: StreakTracker = koinInject(),
  scheduler: TrainingScheduler = koinInject(),
  treeStore: TreeStore = koinInject(),
) {
  val navigator = LocalNavigator.current
  val palette = LocalKineticPalette.current
  val typography = LocalKineticTypography.current

  val stats by
    produceState<TodayStats?>(null, streakTracker, scheduler) {
      val done = streakTracker.cardsCompletedToday()
      val due = scheduler.dueCount()
      value =
        TodayStats(
          streak = streakTracker.streakDays(),
          done = done,
          target = done + due,
          pendingCount = scheduler.pendingCount(),
          week = streakTracker.weekActivity(),
        )
    }
  val pickUpState by
    produceState<PickUpCardState>(PickUpCardState.Loading, treeStore) {
      value = PickUpCardState.Ready(mostRecentRepertoireMastery(treeStore))
    }

  val currentStats = stats ?: return
  val todayIsoIndex = DateUtil.today().dayOfWeek.isoDayNumber

  Column(
    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
    verticalArrangement = Arrangement.spacedBy(20.dp),
  ) {
    TodayHeader(todayIsoIndex)
    StreakBadge(currentStats.streak)
    WeekStrip(week = currentStats.week, todayIsoIndex = todayIsoIndex)
    GoalSection(currentStats)
    StartReviewCta(
      pendingCount = currentStats.pendingCount,
      onClick = { navigator.navigateTo(Route.TrainingRoute.DEFAULT) },
    )
    when (pickUpState) {
      PickUpCardState.Loading -> Unit
      is PickUpCardState.Ready -> {
        val mastery = (pickUpState as PickUpCardState.Ready).mastery
        if (mastery != null) PickUpCard(mastery) else EmptyPickUpCard()
      }
    }
  }
}

/** Weekday name plus the "Ready to review?" greeting at the top of the page. */
@Composable
private fun TodayHeader(todayIsoIndex: Int) {
  val typography = LocalKineticTypography.current
  val palette = LocalKineticPalette.current
  Column {
    Text(
      text = stringResource(weekdayNameRes(todayIsoIndex)).uppercase(),
      style = typography.labelSm.copy(color = palette.ink3),
    )
    Text(
      text = stringResource(Res.string.today_greeting),
      style = typography.displayLg.copy(color = palette.ink),
    )
  }
}

/** The day-count badge, styled like `KineticSideBar`'s streak card. */
@Composable
private fun StreakBadge(streak: Int) {
  val typography = LocalKineticTypography.current
  val palette = LocalKineticPalette.current
  Row(
    modifier =
      Modifier.testTag("today_streak_badge")
        .background(palette.streak, MaterialTheme.shapes.small)
        .padding(vertical = 12.dp, horizontal = 14.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    Text(
      text = streak.toString(),
      maxLines = 1,
      overflow = TextOverflow.Clip,
      style = typography.displayLg.copy(fontSize = 26.sp, color = palette.onStreak),
    )
    Text(
      text = stringResource(Res.string.today_streak_label, streak),
      maxLines = 1,
      style = typography.labelSm.copy(fontWeight = FontWeight.Black, color = palette.onStreak),
    )
  }
}

/** Seven cells, Monday to Sunday, showing this ISO week's activity against [todayIsoIndex]. */
@Composable
private fun WeekStrip(week: List<Boolean>, todayIsoIndex: Int) {
  val palette = LocalKineticPalette.current
  val typography = LocalKineticTypography.current
  Row(
    modifier = Modifier.fillMaxWidth().testTag("today_week_strip"),
    horizontalArrangement = Arrangement.spacedBy(WEEK_CELL_GAP),
  ) {
    for (isoIndex in 1..7) {
      val state = classifyWeekCell(isoIndex, todayIsoIndex, week[isoIndex - 1])
      val background =
        when (state) {
          WeekCellState.DONE -> palette.progress
          WeekCellState.TODAY -> palette.action
          WeekCellState.MISSED -> palette.panel3
          WeekCellState.FUTURE -> palette.panel2
        }
      val content =
        when (state) {
          WeekCellState.DONE -> palette.onProgress
          WeekCellState.TODAY -> palette.onAction
          WeekCellState.MISSED -> palette.ink3
          WeekCellState.FUTURE -> palette.ink4
        }
      Box(
        modifier =
          Modifier.size(WEEK_CELL_SIZE)
            .testTag("today_week_cell_$isoIndex")
            .clip(WEEK_CELL_SHAPE)
            .background(background),
        contentAlignment = Alignment.Center,
      ) {
        Text(
          text = stringResource(weekdayNameRes(isoIndex)).take(1),
          style = typography.label.copy(color = content),
        )
      }
    }
  }
}

/** The goal ring, centered under an "N of M" (or "N done") label. */
@Composable
private fun GoalSection(stats: TodayStats) {
  val typography = LocalKineticTypography.current
  val palette = LocalKineticPalette.current
  val progress = if (stats.target == 0) 0f else stats.done.toFloat() / stats.target
  Box(contentAlignment = Alignment.Center) {
    GoalRing(progress = progress, modifier = Modifier.testTag("today_goal_ring"))
    Text(
      text =
        if (stats.target > 0)
          stringResource(Res.string.today_goal_progress, stats.done, stats.target)
        else stringResource(Res.string.today_goal_done, stats.done),
      maxLines = 1,
      overflow = TextOverflow.Clip,
      style = typography.display.copy(color = palette.ink),
    )
  }
}

/** "Start review" CTA. Always tappable, including when nothing is due. */
@Composable
private fun StartReviewCta(pendingCount: Int, onClick: () -> Unit) {
  KineticButton(
    onClick = onClick,
    style = KineticButtonStyle.Primary,
    large = true,
    modifier = Modifier.fillMaxWidth().testTag("today_cta"),
  ) {
    Text(pluralStringResource(Res.plurals.today_start_review_cta, pendingCount, pendingCount))
  }
}

/** The most recently trained repertoire's "N of M positions solid" card. */
@Composable
private fun PickUpCard(mastery: RepertoireMastery) {
  val typography = LocalKineticTypography.current
  val palette = LocalKineticPalette.current
  Column(
    modifier =
      Modifier.fillMaxWidth()
        .testTag("today_pickup_card")
        .background(palette.panel2, MaterialTheme.shapes.small)
        .padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    Text(
      text = stringResource(Res.string.today_pickup_title),
      style = typography.labelSm.copy(color = palette.ink3),
    )
    Text(text = mastery.repertoireName, style = typography.display.copy(color = palette.ink))
    Text(
      text =
        stringResource(Res.string.today_pickup_progress, mastery.solidCount, mastery.totalCount),
      style = typography.bodySm.copy(color = palette.ink2),
    )
  }
}

/**
 * Rendered instead of [PickUpCard] once nothing has been resolved to show — see [PickUpCardState].
 */
@Composable
private fun EmptyPickUpCard() {
  val typography = LocalKineticTypography.current
  val palette = LocalKineticPalette.current
  Box(
    modifier =
      Modifier.fillMaxWidth()
        .testTag("today_pickup_empty")
        .background(palette.panel2, MaterialTheme.shapes.small)
        .padding(16.dp)
  ) {
    Text(
      text = stringResource(Res.string.today_pickup_empty),
      style = typography.bodySm.copy(color = palette.ink3),
    )
  }
}
