package proj.memorchess.axl.ui.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import proj.memorchess.axl.core.data.repertoire.RepertoireMastery
import proj.memorchess.axl.core.data.repertoire.mostRecentRepertoireMastery
import proj.memorchess.axl.core.date.DateUtil
import proj.memorchess.axl.core.graph.RepertoireTagStore
import proj.memorchess.axl.core.graph.TrainingScheduler
import proj.memorchess.axl.core.streak.StreakTracker
import proj.memorchess.axl.ui.components.buttons.KineticButton
import proj.memorchess.axl.ui.components.buttons.KineticButtonStyle
import proj.memorchess.axl.ui.components.today.GoalRing
import proj.memorchess.axl.ui.components.today.WeekStrip
import proj.memorchess.axl.ui.components.today.weekdayNameRes
import proj.memorchess.axl.ui.pages.navigation.LocalNavigator
import proj.memorchess.axl.ui.pages.navigation.Route
import proj.memorchess.axl.ui.theme.LocalKineticPalette
import proj.memorchess.axl.ui.theme.LocalKineticTypography

/** Snapshot of the numbers the Today page renders. */
private data class TodayStats(
  val streak: Int,
  val done: Int,
  val target: Int,
  val pendingCount: Int,
  val week: List<Boolean>,
)

/**
 * State of the "pick up where you left off" card, telling loading apart from a loaded `null`
 * mastery so the empty card never flashes.
 */
private sealed interface PickUpCardState {
  data object Loading : PickUpCardState

  data class Ready(val mastery: RepertoireMastery?) : PickUpCardState
}

/**
 * Today landing page: streak, week activity, goal ring, review CTA and the last trained repertoire.
 * Renders nothing until its stats resolve; returning from training recomposes it, refreshing them.
 */
@Composable
fun Today(
  streakTracker: StreakTracker = koinInject(),
  scheduler: TrainingScheduler = koinInject(),
  tagStore: RepertoireTagStore = koinInject(),
) {
  val navigator = LocalNavigator.current

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
    produceState<PickUpCardState>(PickUpCardState.Loading, tagStore) {
      value = PickUpCardState.Ready(mostRecentRepertoireMastery(tagStore))
    }

  val currentStats = stats ?: return
  val todayIsoIndex = DateUtil.today().dayOfWeek.isoDayNumber

  Column(
    modifier =
      Modifier.fillMaxSize()
        .testTag(Route.TodayRoute.getLabel())
        .verticalScroll(rememberScrollState())
        .padding(20.dp),
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
    when (val state = pickUpState) {
      PickUpCardState.Loading -> Unit
      is PickUpCardState.Ready -> {
        val mastery = state.mastery
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

/** The day-count streak badge. */
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
      text = pluralStringResource(Res.plurals.today_streak_label, streak),
      maxLines = 1,
      style = typography.labelSm.copy(fontWeight = FontWeight.Black, color = palette.onStreak),
    )
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
      modifier =
        Modifier.testTag(
          if (stats.target > 0) "today_goal_label_progress" else "today_goal_label_done"
        ),
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
    Text(
      text = pluralStringResource(Res.plurals.today_start_review_cta, pendingCount, pendingCount),
      // Tagged with the count so tests can pin the plural boundary without the localized text.
      modifier = Modifier.testTag("today_cta_pending_count_$pendingCount"),
    )
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

/** Shown instead of [PickUpCard] when no repertoire has been trained. */
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
