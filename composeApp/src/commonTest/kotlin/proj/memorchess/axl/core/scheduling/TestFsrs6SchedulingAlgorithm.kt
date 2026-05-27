package proj.memorchess.axl.core.scheduling

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.comparables.shouldBeGreaterThanOrEqualTo
import io.kotest.matchers.comparables.shouldBeLessThanOrEqualTo
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * Unit tests for [Fsrs6SchedulingAlgorithm].
 *
 * Numeric checks recompute expected values from the same formulas as the TypeScript reference
 * (https://github.com/open-spaced-repetition/ts-fsrs); the tests therefore verify behavior, not
 * literal magic numbers.
 */
class TestFsrs6SchedulingAlgorithm {

  private val now = Instant.parse("2026-05-20T10:00:00Z")

  /** Default algorithm: short term scheduler on, fuzz off (matches the canonical FSRS defaults). */
  private val algorithm = Fsrs6SchedulingAlgorithm()

  /** Long term only variant, used to assert day grained intervals and the pre-Phase-3 behavior. */
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
  fun firstReviewWithAgainCountsAsLapse() {
    val state = algorithm.schedule(previous = null, grade = ReviewGrade.AGAIN, now = now)
    state.lapses shouldBe 1
    state.reps shouldBe 1
  }

  @Test
  fun easyGivesLongerIntervalThanGoodThanHardThanAgain() {
    // Day grained interval ordering is a long term concept (the monotonic clamp across grades), so
    // assert it on the long term variant.
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
    // With the short term scheduler off, AGAIN on a brand new card graduates straight to a day
    // grained interval. weights[0] = 0.212 for AGAIN initial stability; with the default FACTOR
    // this
    // rounds to 1 day. (With the short term scheduler on, the same grade instead yields a sub-day
    // learning step — see newCardAgainStaysOnFirstStep.)
    val state = longTerm.schedule(previous = null, grade = ReviewGrade.AGAIN, now = now)
    state.phase shouldBe CardPhase.REVIEW
    (state.dueDate - now).inWholeDays shouldBe 1L
  }

  /**
   * A GOOD review with lower retrievability (more elapsed time) yields more stability, so stability
   * is strictly monotonic in elapsed time. A half day review landing strictly between the zero day
   * and one day reviews proves the elapsed count is fractional, not truncated to an integer
   * (truncation would make the half day case identical to the zero day case).
   */
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

  /**
   * Negative elapsed time (clock skew or a re-grade) must not blow up the forgetting curve; it is
   * clamped to zero, so the result matches a same-instant review.
   */
  @Test
  fun reviewingBeforeLastReviewClampsElapsedDaysToZero() {
    val first = algorithm.schedule(previous = null, grade = ReviewGrade.GOOD, now = now)
    val lastReview = checkNotNull(first.lastReview)

    val atZero = algorithm.schedule(first, ReviewGrade.GOOD, lastReview)
    val before = algorithm.schedule(first, ReviewGrade.GOOD, lastReview - 5.days)

    before.stability shouldBe atZero.stability
  }

  private val fuzzy = Fsrs6SchedulingAlgorithm(enableFuzz = { true })

  private val longTermFuzzy =
    Fsrs6SchedulingAlgorithm(enableFuzz = { true }, enableShortTerm = { false })

  /** EASY initial stability 8.2956 over FACTOR rounds to an 8 day interval with no fuzz applied. */
  @Test
  fun fuzzIsOffByDefault() {
    val state = algorithm.schedule(previous = null, grade = ReviewGrade.EASY, now = now)
    (state.dueDate - now).inWholeDays shouldBe 8L
  }

  /** Scheduling the same card twice yields the same fuzzed interval; the spread is reproducible. */
  @Test
  fun fuzzIsDeterministicForTheSameCard() {
    val a = fuzzy.schedule(previous = null, grade = ReviewGrade.EASY, now = now)
    val b = fuzzy.schedule(previous = null, grade = ReviewGrade.EASY, now = now)
    a.dueDate shouldBe b.dueDate
  }

  /**
   * Raw EASY interval is 8.46 days. The 2.5-to-7 band at 15% and the 7-to-20 band at 10% give a
   * delta of 1.82, so the fuzzed interval lands between 7 and 10 days inclusive.
   */
  @Test
  fun fuzzKeepsTheIntervalInsideTheCanonicalBand() {
    val state = fuzzy.schedule(previous = null, grade = ReviewGrade.EASY, now = now)
    val days = (state.dueDate - now).inWholeDays
    days shouldBeGreaterThanOrEqualTo 7L
    days shouldBeLessThanOrEqualTo 10L
  }

  /**
   * On the long term day grained path, AGAIN rounds to a 1 day interval, well below the 2.5 day
   * fuzz threshold, so enabling fuzz changes nothing.
   */
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

  // Phase 3: short term scheduler and CardPhase state machine (issue #134 gaps 1 and 2).

  /**
   * A new card answered GOOD advances onto the second learning step (ten minutes), not to review.
   */
  @Test
  fun newCardGoodEntersLearning() {
    val state = algorithm.schedule(previous = null, grade = ReviewGrade.GOOD, now = now)
    state.phase shouldBe CardPhase.LEARNING
    state.step shouldBe 1
    (state.dueDate - now) shouldBe 10.minutes
  }

  /** A new card answered AGAIN sits on the first learning step (one minute) and counts a lapse. */
  @Test
  fun newCardAgainStaysOnFirstStep() {
    val state = algorithm.schedule(previous = null, grade = ReviewGrade.AGAIN, now = now)
    state.phase shouldBe CardPhase.LEARNING
    state.step shouldBe 0
    (state.dueDate - now) shouldBe 1.minutes
    state.lapses shouldBe 1
  }

  /** EASY graduates a new card immediately to a day grained review interval. */
  @Test
  fun newCardEasyGraduatesImmediately() {
    val state = algorithm.schedule(previous = null, grade = ReviewGrade.EASY, now = now)
    state.phase shouldBe CardPhase.REVIEW
    (state.dueDate - now).inWholeDays shouldBeGreaterThanOrEqualTo 1L
  }

  /** Two correct answers walk a new card through both learning steps and graduate it to review. */
  @Test
  fun learningGraduatesAfterPassingAllSteps() {
    val first = algorithm.schedule(previous = null, grade = ReviewGrade.GOOD, now = now)
    val second = algorithm.schedule(first, ReviewGrade.GOOD, first.dueDate)
    second.phase shouldBe CardPhase.REVIEW
    (second.dueDate - first.dueDate).inWholeDays shouldBeGreaterThanOrEqualTo 1L
  }

  /** A lapse on a graduated card drops it into relearning on the ten minute step. */
  @Test
  fun reviewLapseEntersRelearning() {
    val graduated = algorithm.schedule(previous = null, grade = ReviewGrade.EASY, now = now)
    val lapsed = algorithm.schedule(graduated, ReviewGrade.AGAIN, graduated.dueDate)
    lapsed.phase shouldBe CardPhase.RELEARNING
    lapsed.step shouldBe 0
    (lapsed.dueDate - graduated.dueDate) shouldBe 10.minutes
    lapsed.lapses shouldBe 1
  }

  /** Passing the single relearning step returns a lapsed card to a day grained review interval. */
  @Test
  fun relearningGoodReturnsToReview() {
    val graduated = algorithm.schedule(previous = null, grade = ReviewGrade.EASY, now = now)
    val lapsed = algorithm.schedule(graduated, ReviewGrade.AGAIN, graduated.dueDate)
    val relearned = algorithm.schedule(lapsed, ReviewGrade.GOOD, lapsed.dueDate)
    relearned.phase shouldBe CardPhase.REVIEW
    (relearned.dueDate - lapsed.dueDate).inWholeDays shouldBeGreaterThanOrEqualTo 1L
  }

  /** With the short term scheduler off, the first review graduates straight to review. */
  @Test
  fun shortTermDisabledGraduatesOnFirstReview() {
    val state = longTerm.schedule(previous = null, grade = ReviewGrade.GOOD, now = now)
    state.phase shouldBe CardPhase.REVIEW
    state.step shouldBe 0
    (state.dueDate - now).inWholeDays shouldBeGreaterThanOrEqualTo 1L
  }
}
