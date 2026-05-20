package proj.memorchess.axl.core.scheduling

import kotlin.math.exp
import kotlin.math.expm1
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.round
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/**
 * Free Spaced Repetition Scheduler, version 6 (long term variant).
 *
 * Ported from the TypeScript reference at https://github.com/open-spaced-repetition/ts-fsrs (files
 * `packages/fsrs/src/constant.ts`, `packages/fsrs/src/algorithm.ts`, and
 * `packages/fsrs/src/abstract_scheduler.ts`). The long term variant grants every review a day
 * grained interval, which fits the chess opening trainer where reviews are always spaced by at
 * least one day.
 *
 * FSRS 6 differs from FSRS 5 in two ways that matter here: the parameter vector has 21 elements
 * (the last one being the decay coefficient), and the FACTOR used when computing the next interval
 * is derived from that decay rather than the fixed 19 over 81 of FSRS 5.
 *
 * @property weights 21 element parameter vector. Defaults to the canonical FSRS 6 weights.
 * @property requestRetention Desired probability of recall when the next review is scheduled.
 * @property maximumInterval Hard cap, in days, on the next interval.
 */
class Fsrs6SchedulingAlgorithm(
  private val weights: DoubleArray = DEFAULT_WEIGHTS,
  private val requestRetention: Double = DEFAULT_REQUEST_RETENTION,
  private val maximumInterval: Int = DEFAULT_MAXIMUM_INTERVAL,
) : SchedulingAlgorithm {

  init {
    require(weights.size == DEFAULT_WEIGHTS.size) {
      "FSRS 6 expects ${DEFAULT_WEIGHTS.size} weights, got ${weights.size}"
    }
  }

  /**
   * Decay coefficient. ts-fsrs stores the absolute value at index 20 and negates inside its
   * forgetting curve; we precompute the negated form so that the rs-fsrs flavored formulas below
   * can use it directly.
   */
  private val decay: Double = -weights[20]

  /**
   * Forgetting curve FACTOR derived from the active decay so that R(stability, stability) =
   * [requestRetention]. Used both inside the forgetting curve and when converting a target
   * stability back to a day interval.
   */
  private val factor: Double = requestRetention.pow(1.0 / decay) - 1.0

  override fun initial(now: Instant): CardState =
    CardState(
      dueDate = now,
      lastReview = null,
      stability = 0.0,
      difficulty = 0.0,
      reps = 0,
      lapses = 0,
    )

  override fun schedule(previous: CardState?, grade: ReviewGrade, now: Instant): CardState {
    val base = previous ?: initial(now)
    return if (base.lastReview == null) {
      firstReview(base, grade, now)
    } else {
      laterReview(base, grade, now)
    }
  }

  private fun firstReview(base: CardState, grade: ReviewGrade, now: Instant): CardState {
    val initialStabilities = ReviewGrade.entries.associateWith { initStability(it) }
    val initialDifficulties = ReviewGrade.entries.associateWith { initDifficulty(it) }
    val intervals =
      monotonicIntervals(initialStabilities.mapValues { (_, s) -> rawIntervalDays(s) })
    val isLapse = grade == ReviewGrade.AGAIN
    return CardState(
      dueDate = now + intervals.getValue(grade).days,
      lastReview = now,
      stability = initialStabilities.getValue(grade),
      difficulty = initialDifficulties.getValue(grade),
      reps = base.reps + 1,
      lapses = base.lapses + if (isLapse) 1 else 0,
    )
  }

  private fun laterReview(base: CardState, grade: ReviewGrade, now: Instant): CardState {
    val lastReview = checkNotNull(base.lastReview)
    val elapsedDays = max(0L, (now - lastReview).inWholeDays)
    val retrievability = forgettingCurve(elapsedDays.toDouble(), base.stability)

    val stabilities =
      ReviewGrade.entries.associateWith { candidate ->
        if (candidate == ReviewGrade.AGAIN) {
          nextForgetStability(base.difficulty, base.stability, retrievability)
        } else {
          nextRecallStability(base.difficulty, base.stability, retrievability, candidate)
        }
      }
    val intervals = monotonicIntervals(stabilities.mapValues { (_, s) -> rawIntervalDays(s) })
    val difficulty = nextDifficulty(base.difficulty, grade)
    val isLapse = grade == ReviewGrade.AGAIN
    return CardState(
      dueDate = now + intervals.getValue(grade).days,
      lastReview = now,
      stability = stabilities.getValue(grade),
      difficulty = difficulty,
      reps = base.reps + 1,
      lapses = base.lapses + if (isLapse) 1 else 0,
    )
  }

  /** Enforces the AGAIN < HARD < GOOD < EASY ordering of intervals. */
  private fun monotonicIntervals(raw: Map<ReviewGrade, Long>): Map<ReviewGrade, Long> {
    var again = raw.getValue(ReviewGrade.AGAIN)
    var hard = raw.getValue(ReviewGrade.HARD)
    var good = raw.getValue(ReviewGrade.GOOD)
    var easy = raw.getValue(ReviewGrade.EASY)
    again = min(again, hard)
    hard = max(hard, again + 1L)
    good = max(good, hard + 1L)
    easy = max(easy, good + 1L)
    val cap = maximumInterval.toLong()
    return mapOf(
      ReviewGrade.AGAIN to again.coerceAtMost(cap),
      ReviewGrade.HARD to hard.coerceAtMost(cap),
      ReviewGrade.GOOD to good.coerceAtMost(cap),
      ReviewGrade.EASY to easy.coerceAtMost(cap),
    )
  }

  // FSRS math.

  private fun forgettingCurve(elapsedDays: Double, stability: Double): Double {
    if (stability <= 0.0) return 1.0
    return (1.0 + factor * elapsedDays / stability).pow(decay)
  }

  private fun initStability(grade: ReviewGrade): Double {
    val w = weights[grade.value - 1]
    return max(w, MIN_STABILITY)
  }

  private fun initDifficulty(grade: ReviewGrade): Double {
    val raw = weights[4] - exp(weights[5] * (grade.value - 1.0)) + 1.0
    return raw.clamp(DIFFICULTY_MIN, DIFFICULTY_MAX)
  }

  private fun nextDifficulty(difficulty: Double, grade: ReviewGrade): Double {
    val proposed = difficulty - weights[6] * (grade.value - 3.0)
    val reverted = meanReversion(initDifficulty(ReviewGrade.EASY), proposed)
    return reverted.clamp(DIFFICULTY_MIN, DIFFICULTY_MAX)
  }

  private fun meanReversion(initial: Double, current: Double): Double =
    weights[7] * initial + (1.0 - weights[7]) * current

  private fun nextRecallStability(
    difficulty: Double,
    stability: Double,
    retrievability: Double,
    grade: ReviewGrade,
  ): Double {
    val modifier =
      when (grade) {
        ReviewGrade.HARD -> weights[15]
        ReviewGrade.EASY -> weights[16]
        else -> 1.0
      }
    val growth =
      exp(weights[8]) *
        (11.0 - difficulty) *
        stability.pow(-weights[9]) *
        expm1((1.0 - retrievability) * weights[10])
    val next = stability * (1.0 + growth * modifier)
    return max(next, MIN_STABILITY)
  }

  private fun nextForgetStability(
    difficulty: Double,
    stability: Double,
    retrievability: Double,
  ): Double {
    val next =
      weights[11] *
        difficulty.pow(-weights[12]) *
        ((stability + 1.0).pow(weights[13]) - 1.0) *
        exp((1.0 - retrievability) * weights[14])
    return max(next, MIN_STABILITY)
  }

  /** Raw interval in days from a target stability, before monotonic ordering or maximum clamp. */
  private fun rawIntervalDays(stability: Double): Long {
    val raw = stability / factor
    val rounded = round(raw).toLong()
    return rounded.coerceAtLeast(1L)
  }

  private fun Double.clamp(lo: Double, hi: Double): Double =
    if (this < lo) lo else if (this > hi) hi else this

  companion object {
    /**
     * Default FSRS 6 weights, taken verbatim from https://github.com/open-spaced-repetition/ts-fsrs
     * (file `packages/fsrs/src/constant.ts`, constant `default_w`). The last element is the
     * absolute value of the decay coefficient.
     */
    val DEFAULT_WEIGHTS: DoubleArray =
      doubleArrayOf(
        0.212,
        1.2931,
        2.3065,
        8.2956,
        6.4133,
        0.8334,
        3.0194,
        0.001,
        1.8722,
        0.1666,
        0.796,
        1.4835,
        0.0614,
        0.2629,
        1.6483,
        0.6014,
        1.8729,
        0.5425,
        0.0912,
        0.0658,
        0.1542,
      )

    private const val MIN_STABILITY: Double = 0.001
    private const val DIFFICULTY_MIN: Double = 1.0
    private const val DIFFICULTY_MAX: Double = 10.0

    const val DEFAULT_REQUEST_RETENTION: Double = 0.9
    const val DEFAULT_MAXIMUM_INTERVAL: Int = 36500
  }
}
