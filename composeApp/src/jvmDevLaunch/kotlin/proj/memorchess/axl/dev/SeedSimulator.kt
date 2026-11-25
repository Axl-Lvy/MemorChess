package proj.memorchess.axl.dev

import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.atTime
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import proj.memorchess.axl.core.data.DailyActivityRecord
import proj.memorchess.axl.core.scheduling.CardPhase
import proj.memorchess.axl.core.scheduling.CardState
import proj.memorchess.axl.core.scheduling.ReviewGrade
import proj.memorchess.axl.core.scheduling.SchedulingAlgorithm

private const val HISTORY_DAYS = 60
private const val REVIEW_SHARE = 0.7
private const val NEVER_INTRODUCED_SHARE = 0.2
private const val MID_LEARNING_YESTERDAY = 5
private const val GUARANTEED_STREAK_DAYS = 14
private const val SKIPPED_DAY_CHANCE = 0.15
private val SESSION_START = LocalTime(19, 0)
private val TIME_PER_REVIEW = 30.seconds
private val GRADE_WEIGHTS =
  listOf(
    ReviewGrade.AGAIN to 0.08,
    ReviewGrade.HARD to 0.12,
    ReviewGrade.GOOD to 0.65,
    ReviewGrade.EASY to 0.15,
  )

/** Final card states and per-day activity produced by replaying a simulated review history. */
internal data class SeedResult<K>(
  val cardStates: Map<K, CardState>,
  val activity: List<DailyActivityRecord>,
)

/**
 * Replays about two months of daily training sessions in memory, through the real [algorithm], so a
 * dev database looks lived in: some cards new, some mid-learning, some due, overdue or mature.
 *
 * Fully determined by [random] and the `now` passed to [simulate], so a fixed seed gives the same
 * history every time.
 */
internal class SeedSimulator(
  private val algorithm: SchedulingAlgorithm,
  private val random: Random,
  private val timeZone: TimeZone,
  private val maxNewPerDay: Int,
  private val maxTotalPerDay: Int,
) {

  /** Simulates training [cards], given in introduction order, up to the day before [now]. */
  fun <K> simulate(cards: List<K>, now: Instant): SeedResult<K> {
    val today = now.toLocalDateTime(timeZone).date
    val toIntroduce =
      ArrayDeque(cards.take((cards.size * (1 - NEVER_INTRODUCED_SHARE)).roundToInt()))
    val states = HashMap<K, CardState>()
    val activity = mutableListOf<DailyActivityRecord>()
    val sessionDays = sessionDays(today)
    sessionDays.forEachIndexed { index, day ->
      val newCount =
        minOf(maxNewPerDay, ceil(toIntroduce.size / (sessionDays.size - index).toDouble()).toInt())
      val newCards = List(minOf(newCount, toIntroduce.size)) { toIntroduce.removeFirst() }
      val reviewed =
        runSession(day, newCards, states, leaveMidLearning = day == today.minus(ONE_DAY))
      if (reviewed > 0) activity += activityRecord(day, reviewed, activity.lastOrNull())
    }
    val untouched = algorithm.initial(now)
    return SeedResult(cards.associateWith { states[it] ?: untouched }, activity)
  }

  private fun sessionDays(today: LocalDate): List<LocalDate> {
    val streakStart = today.minus(DatePeriod(days = GUARANTEED_STREAK_DAYS))
    return (HISTORY_DAYS downTo 1)
      .map { today.minus(DatePeriod(days = it)) }
      .filter { it >= streakStart || random.nextDouble() >= SKIPPED_DAY_CHANCE }
  }

  /** Runs one evening session in place on [states] and returns how many distinct cards it saw. */
  private fun <K> runSession(
    day: LocalDate,
    newCards: List<K>,
    states: MutableMap<K, CardState>,
    leaveMidLearning: Boolean,
  ): Int {
    val sessionStart = day.atTime(SESSION_START).toInstant(timeZone)
    val nextDayStart = day.plus(ONE_DAY).atStartOfDayIn(timeZone)
    val learning = states.filterValues { it.phase.isLearning() }.keys
    val reviewBudget = (maxTotalPerDay - newCards.size - learning.size).coerceAtLeast(0)
    val dueReviews =
      states.entries
        .filter { it.value.phase == CardPhase.REVIEW && it.value.dueDate < nextDayStart }
        .sortedBy { it.value.dueDate }
        .filter { random.nextDouble() < REVIEW_SHARE }
        .take(reviewBudget)
        .map { it.key }
    newCards.forEach { states[it] = algorithm.initial(sessionStart) }
    // Yesterday's session ends before these graduate, which is what fills the learning bucket.
    val stopAfterOneReview =
      if (leaveMidLearning) newCards.takeLast(MID_LEARNING_YESTERDAY) else emptyList()
    val queue = (learning + dueReviews + newCards).toMutableList()
    val seen = HashSet<K>()
    var clock = sessionStart
    while (queue.isNotEmpty()) {
      val card = queue.minBy { states.getValue(it).dueDate }
      queue.remove(card)
      val previous = states.getValue(card)
      clock = maxOf(clock, previous.dueDate)
      val next =
        algorithm.schedule(previous.takeUnless { it.phase == CardPhase.NEW }, grade(), clock)
      states[card] = next
      seen += card
      clock += TIME_PER_REVIEW
      val stillLearningToday = next.phase.isLearning() && next.dueDate < nextDayStart
      if (stillLearningToday && card !in stopAfterOneReview) queue += card
    }
    return seen.size
  }

  private fun grade(): ReviewGrade {
    var roll = random.nextDouble()
    for ((grade, weight) in GRADE_WEIGHTS) {
      if (roll < weight) return grade
      roll -= weight
    }
    return GRADE_WEIGHTS.last().first
  }

  private fun activityRecord(
    day: LocalDate,
    reviewed: Int,
    previous: DailyActivityRecord?,
  ): DailyActivityRecord {
    val continues = previous != null && previous.date.plus(ONE_DAY) == day
    return DailyActivityRecord(
      date = day,
      cardsReviewed = reviewed,
      isActive = true,
      streakLength = if (continues) previous.streakLength + 1 else 1,
    )
  }

  private fun CardPhase.isLearning(): Boolean =
    this == CardPhase.LEARNING || this == CardPhase.RELEARNING

  private companion object {
    val ONE_DAY = DatePeriod(days = 1)
  }
}
