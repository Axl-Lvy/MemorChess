package proj.memorchess.axl.core.graph

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import proj.memorchess.axl.core.data.PositionKey
import proj.memorchess.axl.core.date.DateUtil
import proj.memorchess.axl.core.scheduling.CardPhase
import proj.memorchess.axl.core.scheduling.CardState
import proj.memorchess.axl.core.scheduling.CardStateFactory
import proj.memorchess.axl.core.scheduling.Fsrs6SchedulingAlgorithm
import proj.memorchess.axl.test_util.TestDatabaseQueryManager

class TestTrainingScheduler {

  private val startPos = PositionKey("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq")
  private val posA = PositionKey("posA b K")
  private val posB = PositionKey("posB w K")
  private val posC = PositionKey("posC b K")

  private fun newScheduler(): Pair<TreeStore, TrainingScheduler> {
    val store = TreeStore(TestDatabaseQueryManager.empty())
    val scheduler =
      TrainingScheduler(store, Fsrs6SchedulingAlgorithm(), TimeZone.currentSystemDefault())
    return store to scheduler
  }

  @Test
  fun nextDueReturnsPositionWithGoodOutgoingEdge() = runTest {
    val (store, scheduler) = newScheduler()
    store.addMove(from = startPos, move = "e4", to = posA, isGood = true, fromDepth = 0)
    val entry = scheduler.nextDue()
    assertNotNull(entry)
    assertEquals(startPos, entry.positionKey)
  }

  @Test
  fun nextDuePicksMinimumDepth() = runTest {
    val (store, scheduler) = newScheduler()
    // posA via startPos at depth 0, posB via posA at depth 1
    store.addMove(from = startPos, move = "e4", to = posA, isGood = true, fromDepth = 0)
    store.addMove(from = posA, move = "e5", to = posB, isGood = true, fromDepth = 1)
    val entry = scheduler.nextDue()
    assertNotNull(entry)
    assertEquals(startPos, entry.positionKey)
  }

  @Test
  fun nextDueIgnoresPositionsWithoutGoodOutgoing() = runTest {
    val (store, scheduler) = newScheduler()
    store.addMove(from = startPos, move = "e4", to = posA, isGood = false, fromDepth = 0)
    assertNull(scheduler.nextDue())
  }

  @Test
  fun nextDueSkipsFutureDueDates() = runTest {
    val (store, scheduler) = newScheduler()
    store.addMove(from = startPos, move = "e4", to = posA, isGood = true, fromDepth = 0)
    // Push the start position into the future
    val futureState = CardStateFactory.new(DateUtil.now() + 5.days)
    store.updateCardState(startPos, futureState)
    assertNull(scheduler.nextDue())
  }

  @Test
  fun nextAfterPrefersReachableDestination() = runTest {
    val (store, scheduler) = newScheduler()
    // Two reachable children from startPos
    store.addMove(from = startPos, move = "e4", to = posA, isGood = true, fromDepth = 0)
    store.addMove(from = startPos, move = "d4", to = posB, isGood = true, fromDepth = 0)
    // Each child has its own next move so it is trainable
    store.addMove(from = posA, move = "e5", to = posC, isGood = true, fromDepth = 1)
    store.addMove(from = posB, move = "d5", to = posC, isGood = true, fromDepth = 1)
    val entry = scheduler.nextAfter(startPos)
    assertNotNull(entry)
    // Must be one of the immediate children
    assertEquals(true, entry.positionKey == posA || entry.positionKey == posB)
  }

  @Test
  fun pendingCount() = runTest {
    val (store, scheduler) = newScheduler()
    assertEquals(0, scheduler.pendingCount())
    store.addMove(from = startPos, move = "e4", to = posA, isGood = true, fromDepth = 0)
    store.addMove(from = posA, move = "e5", to = posB, isGood = true, fromDepth = 1)
    assertEquals(2, scheduler.pendingCount())
  }

  // Phase 3 propagation: the tiered selection over CardPhase states (issue #134 gaps 1 and 2).

  @Test
  fun readyLearningCardWithEarlierDueComesFirst() = runTest {
    val (store, scheduler) = newScheduler()
    store.addMove(from = startPos, move = "e4", to = posA, isGood = true, fromDepth = 0)
    store.addMove(from = posA, move = "e5", to = posB, isGood = true, fromDepth = 1)
    val now = DateUtil.now()
    // Both are ready (overdue) learning cards. startPos has the smaller depth, but posA is due
    // earlier, so the earliest-due card wins regardless of depth.
    store.updateCardState(startPos, learningCard(now - 1.minutes))
    store.updateCardState(posA, learningCard(now - 5.minutes))
    assertEquals(posA, scheduler.nextDue()?.positionKey)
  }

  @Test
  fun pendingLearningDoesNotPreemptDueReviewCards() = runTest {
    val (store, scheduler) = newScheduler()
    store.addMove(from = startPos, move = "e4", to = posA, isGood = true, fromDepth = 0)
    store.addMove(from = posA, move = "e5", to = posB, isGood = true, fromDepth = 1)
    // startPos is a fresh (review-like) card due today; posA is a learning card not yet due. The
    // review card must be served first rather than re-showing the just-failed card too early.
    store.updateCardState(posA, learningCard(DateUtil.now() + 10.minutes))
    assertEquals(startPos, scheduler.nextDue()?.positionKey)
  }

  @Test
  fun pendingLearningResurfacesEarliestDueWhenNothingElseIsDue() = runTest {
    val (store, scheduler) = newScheduler()
    store.addMove(from = startPos, move = "e4", to = posA, isGood = true, fromDepth = 0)
    store.addMove(from = posA, move = "e5", to = posB, isGood = true, fromDepth = 1)
    store.addMove(from = posB, move = "Nf3", to = posC, isGood = true, fromDepth = 2)
    val now = DateUtil.now()
    // No review card is due (startPos pushed far into the future); only sub-day learning cards
    // remain, both not yet due. The earliest-due learning card resurfaces — one minute before ten.
    store.updateCardState(startPos, CardStateFactory.new(now + 5.days))
    store.updateCardState(posA, learningCard(now + 10.minutes))
    store.updateCardState(posB, learningCard(now + 1.minutes))
    assertEquals(posB, scheduler.nextDue()?.positionKey)
  }

  private fun learningCard(due: Instant): CardState =
    CardState(
      dueDate = due,
      lastReview = due,
      stability = 1.0,
      difficulty = 5.0,
      reps = 1,
      lapses = 0,
      phase = CardPhase.RELEARNING,
      step = 0,
    )
}
