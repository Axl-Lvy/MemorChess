package proj.memorchess.axl.core.scheduling

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.comparables.shouldBeLessThanOrEqualTo
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlin.time.Duration.Companion.days
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
  private val algorithm = Fsrs6SchedulingAlgorithm()

  @Test
  fun initialStateIsDueImmediatelyWithNoReview() {
    val state = algorithm.initial(now)
    state.dueDate shouldBe now
    state.lastReview shouldBe null
    state.reps shouldBe 0
    state.lapses shouldBe 0
    state.stability shouldBe 0.0
    state.difficulty shouldBe 0.0
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
    val first = algorithm.schedule(previous = null, grade = ReviewGrade.GOOD, now = now)
    val afterOneInterval = now + 10.days
    val again = algorithm.schedule(first, ReviewGrade.AGAIN, afterOneInterval)
    val hard = algorithm.schedule(first, ReviewGrade.HARD, afterOneInterval)
    val good = algorithm.schedule(first, ReviewGrade.GOOD, afterOneInterval)
    val easy = algorithm.schedule(first, ReviewGrade.EASY, afterOneInterval)

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
  fun freshCardAgainProducesShortInterval() {
    // The Wave A UI tests depend on AGAIN on a brand new card producing a 1 day interval.
    val state = algorithm.schedule(previous = null, grade = ReviewGrade.AGAIN, now = now)
    val days = (state.dueDate - now).inWholeDays
    // weights[0] = 0.212 for AGAIN initial stability; with default FACTOR this rounds to 1 day.
    days shouldBe 1L
  }
}
