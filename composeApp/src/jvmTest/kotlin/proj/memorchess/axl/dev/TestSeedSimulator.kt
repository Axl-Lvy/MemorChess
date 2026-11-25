package proj.memorchess.axl.dev

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.ints.shouldBeInRange
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import kotlin.random.Random
import kotlin.test.Test
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import proj.memorchess.axl.core.scheduling.CardPhase
import proj.memorchess.axl.core.scheduling.Fsrs6SchedulingAlgorithm

class TestSeedSimulator {

  private val zone = TimeZone.of("Europe/Paris")

  private val cards = (0 until 300).toList()

  private fun simulator() =
    SeedSimulator(
      algorithm = Fsrs6SchedulingAlgorithm(enableFuzz = { false }, enableShortTerm = { true }),
      random = Random(42),
      timeZone = zone,
      maxNewPerDay = 10,
      maxTotalPerDay = 100,
    )

  private fun at(dateTime: String): Instant = LocalDateTime.parse(dateTime).toInstant(zone)

  private fun today(now: Instant): LocalDate = now.toLocalDateTime(zone).date

  @Test
  fun everyBucketIsFilled() {
    for (now in listOf(at("2026-11-25T10:00"), at("2026-03-29T10:00"), at("2027-01-01T00:30"))) {
      val result = simulator().simulate(cards, now)

      for ((bucket, count) in seedBuckets(result.cardStates.values, now, zone)) {
        withClue(now, bucket) { count shouldBeGreaterThan 0 }
      }
    }
  }

  @Test
  fun sameInputsGiveSameResult() {
    val now = at("2026-11-25T10:00")

    val first = simulator().simulate(cards, now)
    val second = simulator().simulate(cards, now)

    first shouldBe second
  }

  @Test
  fun aboutAFifthStaysNew() {
    val result = simulator().simulate(cards, at("2026-11-25T10:00"))

    result.cardStates.values.count { it.phase == CardPhase.NEW } shouldBeInRange 55..65
  }

  @Test
  fun streakEndsYesterday() {
    val now = at("2026-11-25T10:00")
    val yesterday = today(now).minus(DatePeriod(days = 1))

    val activity = simulator().simulate(cards, now).activity

    activity.filter { it.date == today(now) }.shouldBeEmpty()
    val last = activity.last()
    last.date shouldBe yesterday
    last.isActive shouldBe true
    last.streakLength shouldBeGreaterThanOrEqual 14
    activity.zipWithNext().forEach { (previous, current) ->
      val consecutive = previous.date.plus(DatePeriod(days = 1)) == current.date
      current.streakLength shouldBe if (consecutive) previous.streakLength + 1 else 1
    }
  }

  @Test
  fun capsRespected() {
    val result = simulator().simulate(cards, at("2026-11-25T10:00"))

    val firstReviewsPerDay =
      result.cardStates.values
        .mapNotNull { it.firstReview?.toLocalDateTime(zone)?.date }
        .groupingBy { it }
        .eachCount()
    firstReviewsPerDay.values.shouldNotBeEmpty()
    firstReviewsPerDay.values.forEach { it shouldBeLessThanOrEqual 10 }
    result.activity.forEach { it.cardsReviewed shouldBeLessThanOrEqual 100 }
  }

  private inline fun withClue(now: Instant, bucket: String, block: () -> Unit) =
    io.kotest.assertions.withClue("$bucket at $now") { block() }
}
