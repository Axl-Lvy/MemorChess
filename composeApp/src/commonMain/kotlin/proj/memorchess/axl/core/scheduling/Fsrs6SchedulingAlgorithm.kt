package proj.memorchess.axl.core.scheduling

import kotlin.math.exp
import kotlin.math.expm1
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.round
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
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
 * @property enableFuzz Supplier read on every [schedule] call deciding whether the canonical FSRS
 *   interval fuzz is applied. Defaults to off, matching ts-fsrs' `default_enable_fuzz = false`. It
 *   is a supplier rather than a flat flag so a user toggling the setting takes effect immediately
 *   on the long lived singleton without a restart.
 * @property nextFuzz Source of the per-review fuzz pick, a unit value in `[0, 1)` drawn once per
 *   [schedule] call and shared across the four candidate grades so the spread is keyed on the
 *   review event, not the grade. Defaults to a fresh draw from [Random.Default]; production seeds a
 *   single process-wide [Random] from the wall clock at app start so that two cards graded
 *   identically on the same day still scatter across their fuzz band. Tests inject a fixed-seed
 *   [Random] for reproducibility.
 * @property enableShortTerm Supplier read on every [schedule] call deciding whether the short term
 *   (learning steps) scheduler runs. Defaults to on, matching ts-fsrs' `default_enable_short_term =
 *   true`. When off, every review graduates straight to a day grained [CardPhase.REVIEW] interval,
 *   reproducing the original long term only behavior. Same supplier rationale as [enableFuzz].
 * @property learningSteps Sub-day intervals a new card walks through before graduating. A correct
 *   answer advances one step; passing the last step graduates the card to [CardPhase.REVIEW].
 * @property relearningSteps Sub-day intervals a lapsed review card walks through before returning
 *   to [CardPhase.REVIEW].
 */
class Fsrs6SchedulingAlgorithm(
  private val weights: DoubleArray = DEFAULT_WEIGHTS,
  private val requestRetention: Double = DEFAULT_REQUEST_RETENTION,
  private val maximumInterval: Int = DEFAULT_MAXIMUM_INTERVAL,
  private val enableFuzz: () -> Boolean = { false },
  private val nextFuzz: () -> Double = { Random.Default.nextDouble() },
  private val enableShortTerm: () -> Boolean = { true },
  private val learningSteps: List<Duration> = DEFAULT_LEARNING_STEPS,
  private val relearningSteps: List<Duration> = DEFAULT_RELEARNING_STEPS,
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
      phase = CardPhase.NEW,
      step = 0,
    )

  override fun schedule(previous: CardState?, grade: ReviewGrade, now: Instant): CardState {
    val base = previous ?: initial(now)
    return if (enableShortTerm()) {
      shortTermReview(base, grade, now)
    } else if (base.lastReview == null) {
      firstReview(base, grade, now)
    } else {
      laterReview(base, grade, now)
    }
  }

  /**
   * Short term path: advances the FSRS memory state exactly as the long term path does, then routes
   * the card through the learning, review or relearning steps based on its current
   * [CardState.phase] to pick the next due moment. Sub-day steps keep the card in the active
   * session; graduating to [CardPhase.REVIEW] produces a day grained interval and removes it from
   * the session.
   */
  private fun shortTermReview(base: CardState, grade: ReviewGrade, now: Instant): CardState {
    val lastReview = base.lastReview
    val elapsedDays =
      if (lastReview == null) 0.0
      else max(0.0, (now - lastReview).inWholeMilliseconds / MILLIS_PER_DAY)
    val stability =
      if (lastReview == null) initStability(grade)
      else {
        val retrievability = forgettingCurve(elapsedDays, base.stability)
        if (grade == ReviewGrade.AGAIN) {
          nextForgetStability(base.difficulty, base.stability, retrievability)
        } else {
          nextRecallStability(base.difficulty, base.stability, retrievability, grade)
        }
      }
    val difficulty =
      if (lastReview == null) initDifficulty(grade) else nextDifficulty(base.difficulty, grade)
    val fuzz = nextFuzz()
    val outcome = route(base.phase, base.step, grade, stability, elapsedDays, fuzz)
    // A lapse is a forgotten *graduated* card, matching canonical FSRS. Failing during initial
    // learning or while already relearning does not add another lapse.
    val isLapse = grade == ReviewGrade.AGAIN && base.phase == CardPhase.REVIEW
    return CardState(
      dueDate = now + outcome.delay,
      lastReview = now,
      stability = stability,
      difficulty = difficulty,
      reps = base.reps + 1,
      lapses = base.lapses + if (isLapse) 1 else 0,
      phase = outcome.phase,
      step = outcome.step,
    )
  }

  /** Routes a graded review to its next due delay, phase and step within the state machine. */
  private fun route(
    phase: CardPhase,
    step: Int,
    grade: ReviewGrade,
    stability: Double,
    elapsedDays: Double,
    fuzz: Double,
  ): StepOutcome =
    when (phase) {
      CardPhase.NEW,
      CardPhase.LEARNING ->
        learningRoute(learningSteps, step, grade, CardPhase.LEARNING, stability, elapsedDays, fuzz)
      CardPhase.RELEARNING ->
        learningRoute(
          relearningSteps,
          step,
          grade,
          CardPhase.RELEARNING,
          stability,
          elapsedDays,
          fuzz,
        )
      CardPhase.REVIEW ->
        if (grade == ReviewGrade.AGAIN && relearningSteps.isNotEmpty()) {
          StepOutcome(relearningSteps.first(), CardPhase.RELEARNING, 0)
        } else {
          graduate(stability, elapsedDays, fuzz)
        }
    }

  /**
   * Walks a card through a learning or relearning [steps] ladder. AGAIN drops back to the first
   * step, HARD repeats the current step, GOOD advances one (graduating past the last), and EASY
   * graduates immediately. An empty ladder graduates on any grade.
   */
  private fun learningRoute(
    steps: List<Duration>,
    step: Int,
    grade: ReviewGrade,
    learningPhase: CardPhase,
    stability: Double,
    elapsedDays: Double,
    fuzz: Double,
  ): StepOutcome {
    if (steps.isEmpty()) return graduate(stability, elapsedDays, fuzz)
    return when (grade) {
      ReviewGrade.AGAIN -> StepOutcome(steps.first(), learningPhase, 0)
      ReviewGrade.HARD -> {
        val held = step.coerceIn(steps.indices)
        StepOutcome(steps[held], learningPhase, held)
      }
      ReviewGrade.GOOD -> {
        val next = step + 1
        if (next >= steps.size) graduate(stability, elapsedDays, fuzz)
        else StepOutcome(steps[next], learningPhase, next)
      }
      ReviewGrade.EASY -> graduate(stability, elapsedDays, fuzz)
    }
  }

  /**
   * Produces a graduated [CardPhase.REVIEW] outcome with a day grained interval from [stability].
   */
  private fun graduate(stability: Double, elapsedDays: Double, fuzz: Double): StepOutcome =
    StepOutcome(intervalDays(stability, elapsedDays, fuzz).days, CardPhase.REVIEW, 0)

  private fun firstReview(base: CardState, grade: ReviewGrade, now: Instant): CardState {
    val initialStabilities = ReviewGrade.entries.associateWith { initStability(it) }
    val initialDifficulties = ReviewGrade.entries.associateWith { initDifficulty(it) }
    val fuzz = nextFuzz()
    val intervals =
      monotonicIntervals(
        initialStabilities.mapValues { (_, s) -> intervalDays(s, elapsedDays = 0.0, fuzz) }
      )
    val isLapse = grade == ReviewGrade.AGAIN
    return CardState(
      dueDate = now + intervals.getValue(grade).days,
      lastReview = now,
      stability = initialStabilities.getValue(grade),
      difficulty = initialDifficulties.getValue(grade),
      reps = base.reps + 1,
      lapses = base.lapses + if (isLapse) 1 else 0,
      phase = CardPhase.REVIEW,
      step = 0,
    )
  }

  private fun laterReview(base: CardState, grade: ReviewGrade, now: Instant): CardState {
    val lastReview = checkNotNull(base.lastReview)
    val elapsedDays = max(0.0, (now - lastReview).inWholeMilliseconds / MILLIS_PER_DAY)
    val retrievability = forgettingCurve(elapsedDays, base.stability)

    val stabilities =
      ReviewGrade.entries.associateWith { candidate ->
        if (candidate == ReviewGrade.AGAIN) {
          nextForgetStability(base.difficulty, base.stability, retrievability)
        } else {
          nextRecallStability(base.difficulty, base.stability, retrievability, candidate)
        }
      }
    val fuzz = nextFuzz()
    val intervals =
      monotonicIntervals(stabilities.mapValues { (_, s) -> intervalDays(s, elapsedDays, fuzz) })
    val difficulty = nextDifficulty(base.difficulty, grade)
    val isLapse = grade == ReviewGrade.AGAIN
    return CardState(
      dueDate = now + intervals.getValue(grade).days,
      lastReview = now,
      stability = stabilities.getValue(grade),
      difficulty = difficulty,
      reps = base.reps + 1,
      lapses = base.lapses + if (isLapse) 1 else 0,
      phase = CardPhase.REVIEW,
      step = 0,
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

  /**
   * Interval in days from a target stability, before monotonic ordering or maximum clamp. Applies
   * the canonical fuzz when [enableFuzz] is on; otherwise rounds the raw stability over factor
   * ratio exactly as the unfuzzed path always did.
   *
   * @param fuzz Deterministic unit value in `[0, 1)` shared across the four grades of a single
   *   review so that the fuzz spread is keyed on the card, not on the grade.
   */
  private fun intervalDays(stability: Double, elapsedDays: Double, fuzz: Double): Long {
    val raw = stability / factor
    return if (enableFuzz()) applyFuzz(raw, elapsedDays, fuzz)
    else round(raw).toLong().coerceAtLeast(1L)
  }

  /**
   * Canonical FSRS interval fuzz, ported from ts-fsrs (`help.ts` `get_fuzz_range` and
   * `algorithm.ts` `apply_fuzz`). Intervals below [FUZZ_MIN_INTERVAL] days are returned unfuzzed.
   * Otherwise the interval is spread by a band that widens with the interval ([FUZZ_RANGES]), then
   * a deterministic [fuzz] factor picks a day inside `[minIvl, maxIvl]`. The `interval >
   * elapsedDays` guard keeps the fuzzed interval from scheduling the card before it was actually
   * due.
   */
  private fun applyFuzz(interval: Double, elapsedDays: Double, fuzz: Double): Long {
    if (interval < FUZZ_MIN_INTERVAL) return round(interval).toLong().coerceAtLeast(1L)
    var delta = 1.0
    for (range in FUZZ_RANGES) {
      delta += range.factor * max(min(interval, range.end) - range.start, 0.0)
    }
    var minIvl = max(2.0, round(interval - delta))
    val maxIvl = min(round(interval + delta), maximumInterval.toDouble())
    if (interval > elapsedDays) minIvl = max(minIvl, elapsedDays + 1.0)
    minIvl = min(minIvl, maxIvl)
    return floor(fuzz * (maxIvl - minIvl + 1.0) + minIvl).toLong().coerceAtLeast(1L)
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

    /**
     * Milliseconds in one day, used to derive fractional elapsed days from a
     * [kotlin.time.Duration].
     */
    private const val MILLIS_PER_DAY: Double = 86_400_000.0

    /**
     * Intervals strictly below this many days are never fuzzed (ts-fsrs `apply_fuzz` threshold).
     */
    private const val FUZZ_MIN_INTERVAL: Double = 2.5

    /**
     * Fuzz bands from ts-fsrs (`help.ts` `FUZZ_RANGES`). The spread factor shrinks as intervals
     * grow: 15% in `[2.5, 7)`, 10% in `[7, 20)`, 5% from 20 days onward.
     */
    private val FUZZ_RANGES: List<FuzzRange> =
      listOf(
        FuzzRange(start = 2.5, end = 7.0, factor = 0.15),
        FuzzRange(start = 7.0, end = 20.0, factor = 0.1),
        FuzzRange(start = 20.0, end = Double.POSITIVE_INFINITY, factor = 0.05),
      )

    const val DEFAULT_REQUEST_RETENTION: Double = 0.9
    const val DEFAULT_MAXIMUM_INTERVAL: Int = 36500

    /** Default learning steps for a new card, matching ts-fsrs' `default_learning_steps`. */
    val DEFAULT_LEARNING_STEPS: List<Duration> = listOf(1.minutes, 10.minutes)

    /** Default relearning steps for a lapsed card, matching ts-fsrs' `default_relearning_steps`. */
    val DEFAULT_RELEARNING_STEPS: List<Duration> = listOf(10.minutes)
  }

  /** A single fuzz band: intervals between [start] and [end] days widen the spread by [factor]. */
  private data class FuzzRange(val start: Double, val end: Double, val factor: Double)

  /** The next due [delay], [phase] and learning [step] produced by routing a graded review. */
  private data class StepOutcome(val delay: Duration, val phase: CardPhase, val step: Int)
}
