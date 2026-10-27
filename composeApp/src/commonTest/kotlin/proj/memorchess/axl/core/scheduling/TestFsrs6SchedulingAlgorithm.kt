package proj.memorchess.axl.core.scheduling

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.comparables.shouldBeGreaterThanOrEqualTo
import io.kotest.matchers.comparables.shouldBeLessThanOrEqualTo
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import kotlin.random.Random
import kotlin.test.Test
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * Tests [Fsrs6SchedulingAlgorithm]. Expected numbers derive from the formulas of the reference
 * implementation, https://github.com/open-spaced-repetition/ts-fsrs.
 */
class TestFsrs6SchedulingAlgorithm {

  private val now = Instant.parse("2026-05-20T10:00:00Z")

  /** Default algorithm: short term scheduler on, fuzz off (matches the canonical FSRS defaults). */
  private val algorithm = Fsrs6SchedulingAlgorithm()

  /** Long term only variant, for day grained intervals. */
  private val longTerm = Fsrs6SchedulingAlgorithm(enableShortTerm = { false })

  @Test
  fun initialStateIsDueImmediatelyWithNoReview() {
    val state = algorithm.initial(now)
    state.dueDate shouldBe now
    state.lastReview shouldBe null
    state.reps shouldBe 0
    state.lapses shouldBe 0
    state.stability shouldBe 0.0
    state.difficulty shouldBe 0.0
    state.phase shouldBe CardPhase.NEW
    state.step shouldBe 0
  }

  @Test
  fun firstReviewIsStampedOnFirstGrading() {
    val state = algorithm.schedule(previous = null, grade = ReviewGrade.GOOD, now = now)
    state.firstReview shouldBe now
  }

  @Test
  fun firstReviewIsStampedWhenShortTermIsDisabled() {
    val first = longTerm.schedule(previous = null, grade = ReviewGrade.GOOD, now = now)
    first.firstReview shouldBe now
    val later = longTerm.schedule(first, ReviewGrade.GOOD, now + 10.days)
    later.firstReview shouldBe now
  }

  @Test
  fun firstReviewIsNeverOverwrittenOnLaterReviews() {
    val first = algorithm.schedule(previous = null, grade = ReviewGrade.GOOD, now = now)
    val second = algorithm.schedule(first, ReviewGrade.GOOD, now + 10.minutes)
    val third = algorithm.schedule(second, ReviewGrade.AGAIN, now + 2.days)
    second.firstReview shouldBe now
    third.firstReview shouldBe now
  }

  @Test
  fun firstReviewWithGoodPushesDueDateIntoTheFuture() {
    val state = algorithm.schedule(previous = null, grade = ReviewGrade.GOOD, now = now)
    state.lastReview shouldBe now
    state.reps shouldBe 1
    state.lapses shouldBe 0
    state.stability shouldBeGreaterThan 0.0
    state.difficulty shouldBeGreaterThan 0.0
    state.dueDate shouldBeGreaterThan now
  }

  @Test
  fun firstReviewWithAgainCountsAsLapseInLongTermMode() {
    // Long term graduates the first review to REVIEW, so AGAIN there is a forgotten review.
    val state = longTerm.schedule(previous = null, grade = ReviewGrade.AGAIN, now = now)
    state.lapses shouldBe 1
    state.reps shouldBe 1
  }

  @Test
  fun easyGivesLongerIntervalThanGoodThanHardThanAgain() {
    // The monotonic clamp across grades only exists on the long term path.
    val first = longTerm.schedule(previous = null, grade = ReviewGrade.GOOD, now = now)
    val afterOneInterval = now + 10.days
    val again = longTerm.schedule(first, ReviewGrade.AGAIN, afterOneInterval)
    val hard = longTerm.schedule(first, ReviewGrade.HARD, afterOneInterval)
    val good = longTerm.schedule(first, ReviewGrade.GOOD, afterOneInterval)
    val easy = longTerm.schedule(first, ReviewGrade.EASY, afterOneInterval)

    again.dueDate shouldBeLessThanOrEqualTo hard.dueDate
    hard.dueDate shouldBeLessThanOrEqualTo good.dueDate
    good.dueDate shouldBeLessThanOrEqualTo easy.dueDate
    (easy.dueDate - good.dueDate).inWholeDays shouldBeGreaterThan 0L
    (good.dueDate - hard.dueDate).inWholeDays shouldBeGreaterThan 0L
  }

  @Test
  fun repeatedGoodIncreasesStabilityMonotonically() {
    var state = algorithm.schedule(previous = null, grade = ReviewGrade.GOOD, now = now)
    var moment = now
    repeat(5) {
      val previousStability = state.stability
      moment = state.dueDate
      state = algorithm.schedule(state, ReviewGrade.GOOD, moment)
      state.stability shouldBeGreaterThan previousStability
    }
  }

  @Test
  fun repeatedAgainReducesOrPreservesStability() {
    var state = algorithm.schedule(previous = null, grade = ReviewGrade.GOOD, now = now)
    state = algorithm.schedule(state, ReviewGrade.GOOD, state.dueDate)
    val stableBefore = state.stability
    state.stability shouldBeGreaterThan 0.0

    state = algorithm.schedule(state, ReviewGrade.AGAIN, state.dueDate)
    state.stability shouldBeLessThanOrEqualTo stableBefore
    state.lapses shouldBe 1
  }

  @Test
  fun parameterListMustHaveExactly21Elements() {
    val tooShort = DoubleArray(20) { 0.5 }
    shouldThrow<IllegalArgumentException> { Fsrs6SchedulingAlgorithm(tooShort) }
  }

  @Test
  fun freshCardAgainProducesOneDayIntervalInLongTermMode() {
    // weights[0] = 0.212, the AGAIN initial stability, rounds to 1 day with the default FACTOR.
    val state = longTerm.schedule(previous = null, grade = ReviewGrade.AGAIN, now = now)
    state.phase shouldBe CardPhase.REVIEW
    (state.dueDate - now).inWholeDays shouldBe 1L
  }

  /** Truncating elapsed days would make the half day case equal the zero day one. */
  @Test
  fun fractionalElapsedDaysSitBetweenTheBoundingIntegerDays() {
    val first = algorithm.schedule(previous = null, grade = ReviewGrade.GOOD, now = now)
    val lastReview = checkNotNull(first.lastReview)

    val atZero = algorithm.schedule(first, ReviewGrade.GOOD, lastReview)
    val atHalfDay = algorithm.schedule(first, ReviewGrade.GOOD, lastReview + 12.hours)
    val atOneDay = algorithm.schedule(first, ReviewGrade.GOOD, lastReview + 1.days)
    val atOneYear = algorithm.schedule(first, ReviewGrade.GOOD, lastReview + 365.days)

    atHalfDay.stability shouldBeGreaterThan atZero.stability
    atOneDay.stability shouldBeGreaterThan atHalfDay.stability
    atOneYear.stability shouldBeGreaterThan atOneDay.stability
  }

  /** Clock skew or a re-grade can make elapsed time negative. */
  @Test
  fun reviewingBeforeLastReviewClampsElapsedDaysToZero() {
    val first = algorithm.schedule(previous = null, grade = ReviewGrade.GOOD, now = now)
    val lastReview = checkNotNull(first.lastReview)

    val atZero = algorithm.schedule(first, ReviewGrade.GOOD, lastReview)
    val before = algorithm.schedule(first, ReviewGrade.GOOD, lastReview - 5.days)

    before.stability shouldBe atZero.stability
  }

  // A constant midpoint pick keeps the fuzzed interval deterministic.
  private val fuzzy = Fsrs6SchedulingAlgorithm(enableFuzz = { true }, nextFuzz = { 0.5 })

  private val longTermFuzzy =
    Fsrs6SchedulingAlgorithm(enableFuzz = { true }, nextFuzz = { 0.5 }, enableShortTerm = { false })

  /** EASY initial stability 8.2956 over FACTOR rounds to an 8 day interval with no fuzz applied. */
  @Test
  fun fuzzIsOffByDefault() {
    val state = algorithm.schedule(previous = null, grade = ReviewGrade.EASY, now = now)
    (state.dueDate - now).inWholeDays shouldBe 8L
  }

  @Test
  fun fuzzIsReproducibleUnderAFixedSeed() {
    val a = Fsrs6SchedulingAlgorithm(enableFuzz = { true }, nextFuzz = Random(42)::nextDouble)
    val b = Fsrs6SchedulingAlgorithm(enableFuzz = { true }, nextFuzz = Random(42)::nextDouble)
    val stateA = a.schedule(previous = null, grade = ReviewGrade.EASY, now = now)
    val stateB = b.schedule(previous = null, grade = ReviewGrade.EASY, now = now)
    stateA.dueDate shouldBe stateB.dueDate
  }

  @Test
  fun fuzzSpreadsIdenticalFreshCardsAcrossDifferentDays() {
    val rng = Random(1)
    val algo = Fsrs6SchedulingAlgorithm(enableFuzz = { true }, nextFuzz = rng::nextDouble)
    val intervals =
      (1..20).map {
        (algo.schedule(previous = null, grade = ReviewGrade.EASY, now = now).dueDate - now)
          .inWholeDays
      }
    intervals.toSet().size shouldBeGreaterThan 1
  }

  /**
   * Raw EASY interval is 8.46 days. The 2.5-to-7 band at 15% and the 7-to-20 band at 10% give a
   * delta of 1.82, hence 7 to 10 days inclusive.
   */
  @Test
  fun fuzzKeepsTheIntervalInsideTheCanonicalBand() {
    val state = fuzzy.schedule(previous = null, grade = ReviewGrade.EASY, now = now)
    val days = (state.dueDate - now).inWholeDays
    days shouldBeGreaterThanOrEqualTo 7L
    days shouldBeLessThanOrEqualTo 10L
  }

  /** Long term AGAIN rounds to 1 day, below the 2.5 day fuzz threshold. */
  @Test
  fun fuzzLeavesSubThresholdIntervalsUntouched() {
    val unfuzzed = longTerm.schedule(previous = null, grade = ReviewGrade.AGAIN, now = now)
    val fuzzed = longTermFuzzy.schedule(previous = null, grade = ReviewGrade.AGAIN, now = now)
    fuzzed.dueDate shouldBe unfuzzed.dueDate
    (fuzzed.dueDate - now).inWholeDays shouldBe 1L
  }

  /** Fuzz is applied before the monotonic clamp, so AGAIN < HARD < GOOD < EASY still holds. */
  @Test
  fun fuzzStillRespectsMonotonicOrdering() {
    val first = longTermFuzzy.schedule(previous = null, grade = ReviewGrade.GOOD, now = now)
    val moment = now + 10.days
    val again = longTermFuzzy.schedule(first, ReviewGrade.AGAIN, moment)
    val hard = longTermFuzzy.schedule(first, ReviewGrade.HARD, moment)
    val good = longTermFuzzy.schedule(first, ReviewGrade.GOOD, moment)
    val easy = longTermFuzzy.schedule(first, ReviewGrade.EASY, moment)

    again.dueDate shouldBeLessThanOrEqualTo hard.dueDate
    hard.dueDate shouldBeLessThanOrEqualTo good.dueDate
    good.dueDate shouldBeLessThanOrEqualTo easy.dueDate
  }

  @Test
  fun newCardGoodEntersLearning() {
    val state = algorithm.schedule(previous = null, grade = ReviewGrade.GOOD, now = now)
    state.phase shouldBe CardPhase.LEARNING
    state.step shouldBe 1
    (state.dueDate - now) shouldBe 10.minutes
  }

  /** FSRS counts a lapse only for a forgotten graduated card. */
  @Test
  fun newCardAgainStaysOnFirstStep() {
    val state = algorithm.schedule(previous = null, grade = ReviewGrade.AGAIN, now = now)
    state.phase shouldBe CardPhase.LEARNING
    state.step shouldBe 0
    (state.dueDate - now) shouldBe 1.minutes
    state.lapses shouldBe 0
  }

  @Test
  fun newCardEasyGraduatesImmediately() {
    val state = algorithm.schedule(previous = null, grade = ReviewGrade.EASY, now = now)
    state.phase shouldBe CardPhase.REVIEW
    (state.dueDate - now).inWholeDays shouldBeGreaterThanOrEqualTo 1L
  }

  @Test
  fun learningGraduatesAfterPassingAllSteps() {
    val first = algorithm.schedule(previous = null, grade = ReviewGrade.GOOD, now = now)
    val second = algorithm.schedule(first, ReviewGrade.GOOD, first.dueDate)
    second.phase shouldBe CardPhase.REVIEW
    (second.dueDate - first.dueDate).inWholeDays shouldBeGreaterThanOrEqualTo 1L
  }

  @Test
  fun reviewLapseEntersRelearning() {
    val graduated = algorithm.schedule(previous = null, grade = ReviewGrade.EASY, now = now)
    val lapsed = algorithm.schedule(graduated, ReviewGrade.AGAIN, graduated.dueDate)
    lapsed.phase shouldBe CardPhase.RELEARNING
    lapsed.step shouldBe 0
    (lapsed.dueDate - graduated.dueDate) shouldBe 10.minutes
    lapsed.lapses shouldBe 1
  }

  @Test
  fun failingInRelearningDoesNotDoubleCountLapses() {
    val graduated = algorithm.schedule(previous = null, grade = ReviewGrade.EASY, now = now)
    val lapsed = algorithm.schedule(graduated, ReviewGrade.AGAIN, graduated.dueDate)
    val stillRelearning = algorithm.schedule(lapsed, ReviewGrade.AGAIN, lapsed.dueDate)
    lapsed.lapses shouldBe 1
    stillRelearning.phase shouldBe CardPhase.RELEARNING
    stillRelearning.lapses shouldBe 1
  }

  @Test
  fun relearningGoodReturnsToReview() {
    val graduated = algorithm.schedule(previous = null, grade = ReviewGrade.EASY, now = now)
    val lapsed = algorithm.schedule(graduated, ReviewGrade.AGAIN, graduated.dueDate)
    val relearned = algorithm.schedule(lapsed, ReviewGrade.GOOD, lapsed.dueDate)
    relearned.phase shouldBe CardPhase.REVIEW
    (relearned.dueDate - lapsed.dueDate).inWholeDays shouldBeGreaterThanOrEqualTo 1L
  }

  @Test
  fun shortTermDisabledGraduatesOnFirstReview() {
    val state = longTerm.schedule(previous = null, grade = ReviewGrade.GOOD, now = now)
    state.phase shouldBe CardPhase.REVIEW
    state.step shouldBe 0
    (state.dueDate - now).inWholeDays shouldBeGreaterThanOrEqualTo 1L
  }
}
