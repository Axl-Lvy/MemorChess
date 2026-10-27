package proj.memorchess.axl.core.graph

import kotlin.time.Instant
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import proj.memorchess.axl.core.data.DatabaseQueryManager
import proj.memorchess.axl.core.data.PositionKey
import proj.memorchess.axl.core.data.SchedulingCounts
import proj.memorchess.axl.core.date.DateUtil
import proj.memorchess.axl.core.scheduling.CardPhase
import proj.memorchess.axl.core.scheduling.ReviewGrade
import proj.memorchess.axl.core.scheduling.SchedulingAlgorithm
import proj.memorchess.axl.core.streak.StreakTracker

/**
 * Spaced repetition driver for the opening tree, built on bounded [DatabaseQueryManager] queries
 * that never enumerate the repertoire.
 *
 * [maxNewMovesPerDay] and [maxTotalMovesPerDay] cap distinct positions per calendar day in
 * [timeZone]; like Anki, cards mid learning steps are exempt. The caps are suppliers so a settings
 * change applies immediately, and they stay global when a call is scoped to one repertoire.
 */
class TrainingScheduler(
  private val database: DatabaseQueryManager,
  private val treeStore: TreeStore,
  private val tagStore: RepertoireTagStore,
  private val algorithm: SchedulingAlgorithm,
  private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
  private val maxNewMovesPerDay: () -> Int,
  private val maxTotalMovesPerDay: () -> Int,
  private val streakTracker: StreakTracker? = null,
) {

  /**
   * Returns the next [TrainingEntry] to train for [day], or `null` when nothing is left. The entry
   * stays scheduled until [grade] graduates it to a day grained interval.
   *
   * @param repertoireId When not `null`, only positions trainable within this repertoire are
   *   served.
   */
  suspend fun nextDue(
    day: LocalDate = DateUtil.today(timeZone),
    repertoireId: String? = null,
  ): TrainingEntry? {
    val now = DateUtil.now()
    val (dayStart, dayEnd) = dayBounds(day)
    val counts = database.getSchedulingCounts(dayStart, dayEnd)
    val newRemaining = (maxNewMovesPerDay() - counts.introducedToday).coerceAtLeast(0)
    val totalRemaining = (maxTotalMovesPerDay() - counts.trainedToday).coerceAtLeast(0)

    database.nextReadyLearningCard(now, repertoireId)?.let {
      return it
    }
    if (totalRemaining > 0) {
      database.nextDueReviewCard(dayEnd, repertoireId)?.let {
        return it
      }
    }
    if (newRemaining > 0 && totalRemaining > 0) {
      database.nextDueNewCard(dayEnd, repertoireId)?.let {
        return it
      }
    }
    // Falling through to not yet due learning cards keeps a just failed card in the session.
    return database.nextPendingLearningCard(now, repertoireId)
  }

  /**
   * Returns a trainable entry reachable through one of [position]'s outgoing edges, or `null` when
   * none qualifies within the daily caps, so a session can follow the current line.
   *
   * @param repertoireId When not `null`, only edges tagged with this repertoire are followed.
   */
  suspend fun nextAfter(
    position: PositionKey,
    day: LocalDate = DateUtil.today(timeZone),
    repertoireId: String? = null,
  ): TrainingEntry? {
    val node = treeStore.node(position) ?: return null
    val destinations =
      if (repertoireId == null) {
        node.outgoing.values.map { it.to }
      } else {
        node.outgoing.values
          .filter { repertoireId in tagStore.tagsFor(it.from, it.to) }
          .map { it.to }
      }
    val (dayStart, dayEnd) = dayBounds(day)
    val counts = database.getSchedulingCounts(dayStart, dayEnd)
    val totalRemaining = (maxTotalMovesPerDay() - counts.trainedToday).coerceAtLeast(0)
    if (totalRemaining <= 0) return null
    val newRemaining = (maxNewMovesPerDay() - counts.introducedToday).coerceAtLeast(0)
    val candidate = database.findEligibleAmong(destinations, dayEnd, repertoireId) ?: return null
    if (candidate.cardState.phase == CardPhase.NEW && newRemaining <= 0) return null
    return candidate
  }

  /**
   * Counts entries still to train for [day]: in-session cards plus the due reviews and new cards
   * the daily caps still allow.
   *
   * @param repertoireId When not `null`, counts only this repertoire's cards.
   */
  suspend fun pendingCount(
    day: LocalDate = DateUtil.today(timeZone),
    repertoireId: String? = null,
  ): Int {
    val (dayStart, dayEnd) = dayBounds(day)
    val globalCounts = database.getSchedulingCounts(dayStart, dayEnd)
    val scoped = repertoireId?.let { database.getScopedCounts(dayEnd, it) }
    val (reviewsServable, newServable) =
      servableCounts(
        globalCounts,
        dueReviews = scoped?.dueReviews ?: globalCounts.dueReviews,
        dueNew = scoped?.dueNew ?: globalCounts.dueNew,
      )
    val inSession = scoped?.inSession ?: globalCounts.inSession
    return inSession + reviewsServable + newServable
  }

  /**
   * Counts due reviews and new cards the daily caps still allow for [day], excluding in-session
   * cards: [StreakTracker.cardsCompletedToday] already counts them, so adding them would double
   * count.
   *
   * @param repertoireId When not `null`, counts only this repertoire's cards.
   */
  suspend fun dueCount(
    day: LocalDate = DateUtil.today(timeZone),
    repertoireId: String? = null,
  ): Int {
    val (dayStart, dayEnd) = dayBounds(day)
    val globalCounts = database.getSchedulingCounts(dayStart, dayEnd)
    val scoped = repertoireId?.let { database.getScopedCounts(dayEnd, it) }
    val (reviewsServable, newServable) =
      servableCounts(
        globalCounts,
        dueReviews = scoped?.dueReviews ?: globalCounts.dueReviews,
        dueNew = scoped?.dueNew ?: globalCounts.dueNew,
      )
    return reviewsServable + newServable
  }

  /** Due reviews and due new cards servable once the daily caps are applied, in that order. */
  private fun servableCounts(
    caps: SchedulingCounts,
    dueReviews: Int = caps.dueReviews,
    dueNew: Int = caps.dueNew,
  ): Pair<Int, Int> {
    val totalRemaining = (maxTotalMovesPerDay() - caps.trainedToday).coerceAtLeast(0)
    val newRemaining = (maxNewMovesPerDay() - caps.introducedToday).coerceAtLeast(0)
    val reviewsServable = minOf(dueReviews, totalRemaining)
    val newBudget = minOf(newRemaining, totalRemaining - reviewsServable).coerceAtLeast(0)
    val newServable = minOf(dueNew, newBudget)
    return reviewsServable to newServable
  }

  /** Persists the result of a review for [position] and feeds the streak. */
  suspend fun grade(position: PositionKey, grade: ReviewGrade) {
    val node = treeStore.node(position) ?: return
    val now = DateUtil.now()
    val previousLastReview = node.cardState.lastReview
    val nextState = algorithm.schedule(node.cardState, grade, now)
    treeStore.updateCardState(position, nextState)
    if (streakTracker != null) {
      val day = now.toLocalDateTime(timeZone).date
      val (dayStart, _) = dayBounds(day)
      val alreadyReviewedToday = previousLastReview != null && previousLastReview >= dayStart
      // An empty streak day still records: today's earlier review may have arrived via sync.
      if (!alreadyReviewedToday || streakTracker.cardsCompletedToday(day) == 0) {
        streakTracker.recordReview(day)
      }
    }
  }

  /** Positions reviewed for the first time today, or `0` without a [streakTracker]. */
  suspend fun cardsCompletedToday(): Int =
    streakTracker?.cardsCompletedToday(DateUtil.now().toLocalDateTime(timeZone).date) ?: 0

  /** Current daily-review streak in days, or `0` when [streakTracker] is absent. */
  suspend fun streakDays(): Int =
    streakTracker?.streakDays(DateUtil.now().toLocalDateTime(timeZone).date) ?: 0

  /** Returns the half open window `[dayStart, dayEndExclusive)` of [day] in [timeZone]. */
  private fun dayBounds(day: LocalDate): Pair<Instant, Instant> {
    val dayStart = day.atStartOfDayIn(timeZone)
    val dayEndExclusive = day.plus(1, DateTimeUnit.DAY).atStartOfDayIn(timeZone)
    return dayStart to dayEndExclusive
  }
}
