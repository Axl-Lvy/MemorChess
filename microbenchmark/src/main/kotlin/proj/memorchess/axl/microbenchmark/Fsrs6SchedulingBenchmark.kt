package proj.memorchess.axl.microbenchmark

import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.Blackhole
import kotlinx.benchmark.Scope
import kotlinx.benchmark.Setup
import kotlinx.benchmark.State
import proj.memorchess.axl.core.scheduling.CardPhase
import proj.memorchess.axl.core.scheduling.CardState
import proj.memorchess.axl.core.scheduling.Fsrs6SchedulingAlgorithm
import proj.memorchess.axl.core.scheduling.ReviewGrade

/**
 * Measures [Fsrs6SchedulingAlgorithm] over a deterministic batch covering every grade and phase.
 * The trainer schedules on the UI thread, so a slowdown here delays the next card.
 */
@State(Scope.Benchmark)
class Fsrs6SchedulingBenchmark {

  private val now: Instant = Instant.parse("2026-01-01T00:00:00Z")

  /** Short term scheduler with the learning step ladders active, the app's default mode. */
  private val shortTermAlgorithm =
    Fsrs6SchedulingAlgorithm(enableFuzz = { true }, nextFuzz = { 0.5 }, enableShortTerm = { true })

  /** Long term only scheduler, the mode used when the user disables learning steps. */
  private val longTermAlgorithm =
    Fsrs6SchedulingAlgorithm(enableFuzz = { true }, nextFuzz = { 0.5 }, enableShortTerm = { false })

  private var cards: List<CardState?> = emptyList()

  @Setup
  fun setup() {
    // The null entry exercises the first review path.
    cards =
      listOf(null) +
        (0 until 100).map { i ->
          CardState(
            dueDate = now,
            lastReview = now - ((i % 30) + 1).days,
            firstReview = now - ((i % 60) + 1).days,
            stability = 0.4 + i * 0.37,
            difficulty = 1.0 + (i % 91) / 10.0,
            reps = i,
            lapses = i % 5,
            phase = CardPhase.entries[i % CardPhase.entries.size],
            step = i % 2,
          )
        }
  }

  /** Schedules every card with all four grades through the short term state machine. */
  @Benchmark
  fun shortTermSchedulingPass(bh: Blackhole) {
    for (card in cards) {
      for (grade in ReviewGrade.entries) {
        bh.consume(shortTermAlgorithm.schedule(card, grade, now))
      }
    }
  }

  /** Schedules every card with all four grades through the long term path. */
  @Benchmark
  fun longTermSchedulingPass(bh: Blackhole) {
    for (card in cards) {
      for (grade in ReviewGrade.entries) {
        bh.consume(longTermAlgorithm.schedule(card, grade, now))
      }
    }
  }
}
