package proj.memorchess.axl.core.graph

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.UtcOffset
import kotlinx.datetime.asTimeZone
import kotlinx.datetime.toInstant
import proj.memorchess.axl.core.data.DataMove
import proj.memorchess.axl.core.data.DataNode
import proj.memorchess.axl.core.data.PositionKey
import proj.memorchess.axl.core.date.DateUtil
import proj.memorchess.axl.core.scheduling.CardPhase
import proj.memorchess.axl.core.scheduling.CardState
import proj.memorchess.axl.core.scheduling.CardStateFactory
import proj.memorchess.axl.core.scheduling.Fsrs6SchedulingAlgorithm
import proj.memorchess.axl.test_util.TestDatabases

class TestTrainingScheduler {

  private val startPos = PositionKey("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq")
  private val posA = PositionKey("posA b K")
  private val posB = PositionKey("posB w K")
  private val posC = PositionKey("posC b K")

  private fun newScheduler(
    maxNew: Int = Int.MAX_VALUE,
    maxTotal: Int = Int.MAX_VALUE,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
  ): Pair<TreeStore, TrainingScheduler> {
    val database = TestDatabases.empty()
    val store = TreeStore(database)
    val scheduler =
      TrainingScheduler(
        database,
        store,
        Fsrs6SchedulingAlgorithm(),
        timeZone,
        { maxNew },
        { maxTotal },
      )
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

  @Test
  fun nextDueServesDueReviewCardsBeforeNewCards() = runTest {
    val (store, scheduler) = newScheduler()
    store.addMove(from = startPos, move = "e4", to = posA, isGood = true, fromDepth = 0)
    store.addMove(from = posA, move = "e5", to = posB, isGood = true, fromDepth = 1)
    // startPos is a brand new card at depth 0; posA is a graduated review card due in the past at
    // depth 1. The due review wins even though the new card is shallower.
    store.updateCardState(posA, reviewCard(DateUtil.now() - 1.days))
    assertEquals(posA, scheduler.nextDue()?.positionKey)
  }

  @Test
  fun nextDuePicksNewCardsByDepthThenCreatedAt() = runTest {
    // New-card ordering is the approximate fallback: shallower positions first, ties broken by the
    // earliest creation. The e4 line is older (smaller createdAt) but reaches posB at depth 2; the
    // d4 line is newer but reaches posC at depth 1. Once startPos and posA are no longer due, the
    // shallower posC must surface before the deeper posB, even though its line was added later.
    val t1 = Instant.parse("2026-01-01T00:00:00Z")
    val t2 = Instant.parse("2026-01-02T00:00:00Z")
    val endOfOldLine = PositionKey("endOld w K")
    val endOfNewLine = PositionKey("endNew w K")
    val e4 = dataMove(startPos, "e4", posA, t1)
    val e5 = dataMove(posA, "e5", posB, t1)
    val g3 = dataMove(posB, "g3", endOfOldLine, t1)
    val d4 = dataMove(startPos, "d4", posC, t2)
    val d5 = dataMove(posC, "d5", endOfNewLine, t2)
    val database = TestDatabases.empty()
    database.insertNodes(
      dataNode(startPos, 0, emptyList(), listOf(e4, d4), createdAt = t1),
      dataNode(posA, 1, listOf(e4), listOf(e5), createdAt = t1),
      dataNode(posB, 2, listOf(e5), listOf(g3), createdAt = t1),
      dataNode(endOfOldLine, 3, listOf(g3), emptyList(), createdAt = t1, hasGoodOutgoing = false),
      dataNode(posC, 1, listOf(d4), listOf(d5), createdAt = t2),
      dataNode(endOfNewLine, 2, listOf(d5), emptyList(), createdAt = t2, hasGoodOutgoing = false),
    )
    val store = TreeStore(database)
    store.load()
    val scheduler =
      TrainingScheduler(
        database,
        store,
        Fsrs6SchedulingAlgorithm(),
        TimeZone.currentSystemDefault(),
      )
    val now = DateUtil.now()
    store.updateCardState(startPos, CardStateFactory.new(now + 5.days))
    store.updateCardState(posA, CardStateFactory.new(now + 5.days))
    assertEquals(posC, scheduler.nextDue()?.positionKey)
  }

  // Daily caps: how many new moves and how many total moves are served per day.

  @Test
  fun newLimitZeroServesNoNewCards() = runTest {
    val (store, scheduler) = newScheduler(maxNew = 0)
    store.addMove(from = startPos, move = "e4", to = posA, isGood = true, fromDepth = 0)
    store.addMove(from = posA, move = "e5", to = posB, isGood = true, fromDepth = 1)
    assertEquals(0, scheduler.pendingCount())
    assertNull(scheduler.nextDue())
  }

  @Test
  fun newLimitOneServesExactlyOneNewCard() = runTest {
    val (store, scheduler) = newScheduler(maxNew = 1)
    store.addMove(from = startPos, move = "e4", to = posA, isGood = true, fromDepth = 0)
    store.addMove(from = posA, move = "e5", to = posB, isGood = true, fromDepth = 1)
    assertEquals(1, scheduler.pendingCount())
    assertEquals(startPos, scheduler.nextDue()?.positionKey)
  }

  @Test
  fun newLimitExactlyAtBoundaryServesAllNewCards() = runTest {
    val (store, scheduler) = newScheduler(maxNew = 2)
    store.addMove(from = startPos, move = "e4", to = posA, isGood = true, fromDepth = 0)
    store.addMove(from = posA, move = "e5", to = posB, isGood = true, fromDepth = 1)
    assertEquals(2, scheduler.pendingCount())
  }

  @Test
  fun newLimitOneAboveBoundaryServesAllNewCards() = runTest {
    val (store, scheduler) = newScheduler(maxNew = 3)
    store.addMove(from = startPos, move = "e4", to = posA, isGood = true, fromDepth = 0)
    store.addMove(from = posA, move = "e5", to = posB, isGood = true, fromDepth = 1)
    assertEquals(2, scheduler.pendingCount())
  }

  @Test
  fun largeNewLimitServesEverything() = runTest {
    val (store, scheduler) = newScheduler(maxNew = 1_000_000)
    store.addMove(from = startPos, move = "e4", to = posA, isGood = true, fromDepth = 0)
    store.addMove(from = posA, move = "e5", to = posB, isGood = true, fromDepth = 1)
    assertEquals(2, scheduler.pendingCount())
  }

  @Test
  fun totalLimitZeroStillServesInSessionLearningCards() = runTest {
    val (store, scheduler) = newScheduler(maxTotal = 0)
    store.addMove(from = startPos, move = "e4", to = posA, isGood = true, fromDepth = 0)
    store.addMove(from = posA, move = "e5", to = posB, isGood = true, fromDepth = 1)
    // posA is mid learning and ready. Even with the total cap at zero it must finish its steps,
    // while the brand new startPos card stays blocked.
    store.updateCardState(posA, learningCard(DateUtil.now() - 1.minutes))
    assertEquals(1, scheduler.pendingCount())
    assertEquals(posA, scheduler.nextDue()?.positionKey)
  }

  @Test
  fun totalLimitConsumedByReviewsBeforeNewCards() = runTest {
    val (store, scheduler) = newScheduler(maxTotal = 1)
    store.addMove(from = startPos, move = "e4", to = posA, isGood = true, fromDepth = 0)
    store.addMove(from = posA, move = "e5", to = posB, isGood = true, fromDepth = 1)
    // startPos is a due review; posA is a brand new card. With only one slot left, the review is
    // served and the new card is held back.
    store.updateCardState(startPos, reviewCard(DateUtil.now() - 1.days))
    assertEquals(1, scheduler.pendingCount())
    assertEquals(startPos, scheduler.nextDue()?.positionKey)
  }

  @Test
  fun totalLimitReachedByCardsTrainedTodayServesNothing() = runTest {
    val (store, scheduler) = newScheduler(maxTotal = 1)
    store.addMove(from = startPos, move = "e4", to = posA, isGood = true, fromDepth = 0)
    store.addMove(from = posA, move = "e5", to = posB, isGood = true, fromDepth = 1)
    store.addMove(from = posC, move = "Nf3", to = posB, isGood = true, fromDepth = 0)
    // posC was already trained today (and is not due again), so it consumes the single daily slot
    // and nothing else is served.
    store.updateCardState(posC, reviewedTodayCard())
    assertEquals(0, scheduler.pendingCount())
    assertNull(scheduler.nextDue())
  }

  @Test
  fun pendingCountReflectsTheNewCap() = runTest {
    val (store, scheduler) = newScheduler(maxNew = 1, maxTotal = 5)
    store.addMove(from = startPos, move = "e4", to = posA, isGood = true, fromDepth = 0)
    store.addMove(from = posA, move = "e5", to = posB, isGood = true, fromDepth = 1)
    store.addMove(from = posB, move = "Nf3", to = posC, isGood = true, fromDepth = 2)
    assertEquals(1, scheduler.pendingCount())
  }

  @Test
  fun nextAfterServesAnEligibleReachableChildIgnoringCaps() = runTest {
    val (store, scheduler) = newScheduler(maxNew = 0)
    store.addMove(from = startPos, move = "e4", to = posA, isGood = true, fromDepth = 0)
    store.addMove(from = posA, move = "e5", to = posB, isGood = true, fromDepth = 1)
    // nextAfter is a best-effort "stay in the current line" lookup: it returns the first eligible
    // reachable child (trainable and due today) without consuming or honoring the daily caps. The
    // cap is enforced by nextDue, which the trainer falls back to. So posA surfaces even with the
    // new-card cap at zero.
    assertEquals(posA, scheduler.nextAfter(startPos)?.positionKey)
  }

  @Test
  fun nextAfterReturnsNullWhenNoReachableChildIsEligible() = runTest {
    val (store, scheduler) = newScheduler()
    // startPos's only child posA has no good outgoing edge, so it is not trainable and not
    // eligible; nextAfter then finds nothing reachable.
    store.addMove(from = startPos, move = "e4", to = posA, isGood = true, fromDepth = 0)
    assertNull(scheduler.nextAfter(startPos))
  }

  @Test
  fun countsResetAtLocalMidnight() = runTest {
    // A fixed offset zone, distinct from UTC, so the rollover is keyed on the configured zone
    // rather than UTC. A named IANA zone would need a timezone database the wasmJs browser lacks.
    val zone = UtcOffset(hours = 1).asTimeZone()
    val (store, scheduler) = newScheduler(maxNew = 1, maxTotal = 1, timeZone = zone)
    val today = LocalDate(2026, 6, 13)
    val yesterdayLate = LocalDateTime(2026, 6, 12, 23, 59).toInstant(zone)
    // posC was introduced and trained yesterday at 23:59 local. After midnight that no longer
    // counts against today's budget.
    store.addMove(from = posC, move = "Nf3", to = posB, isGood = true, fromDepth = 0)
    store.updateCardState(
      posC,
      CardState(
        dueDate = yesterdayLate + 5.days,
        lastReview = yesterdayLate,
        firstReview = yesterdayLate,
        stability = 10.0,
        difficulty = 5.0,
        reps = 2,
        lapses = 0,
        phase = CardPhase.REVIEW,
        step = 0,
      ),
    )
    val todayMorning = LocalDateTime(2026, 6, 13, 9, 0).toInstant(zone)
    store.addMove(from = startPos, move = "e4", to = posA, isGood = true, fromDepth = 0)
    store.updateCardState(startPos, CardStateFactory.new(todayMorning))
    assertEquals(1, scheduler.pendingCount(today))
    assertEquals(startPos, scheduler.nextDue(today)?.positionKey)
  }

  @Test
  fun emptyRepertoireServesNothing() = runTest {
    val (_, scheduler) = newScheduler()
    assertNull(scheduler.nextDue())
    assertEquals(0, scheduler.pendingCount())
    assertNull(scheduler.nextAfter(startPos))
  }

  @Test
  fun reviewDueExactlyAtNextMidnightIsNotServedToday() = runTest {
    val zone = UtcOffset(hours = 1).asTimeZone()
    val (store, scheduler) = newScheduler(timeZone = zone)
    val today = LocalDate(2026, 6, 13)
    // dueDate sits exactly on the exclusive day boundary (next local midnight). Because the bound
    // is exclusive, the review belongs to the next day and must not be served for today.
    val nextMidnight = LocalDateTime(2026, 6, 14, 0, 0).toInstant(zone)
    store.addMove(from = startPos, move = "e4", to = posA, isGood = true, fromDepth = 0)
    store.updateCardState(startPos, reviewCard(nextMidnight))
    assertNull(scheduler.nextDue(today))
    assertEquals(0, scheduler.pendingCount(today))
  }

  @Test
  fun reviewDueOneSecondBeforeMidnightIsServedToday() = runTest {
    val zone = UtcOffset(hours = 1).asTimeZone()
    val (store, scheduler) = newScheduler(timeZone = zone)
    val today = LocalDate(2026, 6, 13)
    // One second below the exclusive boundary: still due today.
    val justBeforeMidnight = LocalDateTime(2026, 6, 13, 23, 59, 59).toInstant(zone)
    store.addMove(from = startPos, move = "e4", to = posA, isGood = true, fromDepth = 0)
    store.updateCardState(startPos, reviewCard(justBeforeMidnight))
    assertEquals(startPos, scheduler.nextDue(today)?.positionKey)
    assertEquals(1, scheduler.pendingCount(today))
  }

  private fun reviewedTodayCard(): CardState {
    val now = DateUtil.now()
    return CardState(
      dueDate = now + 5.days,
      lastReview = now,
      firstReview = now,
      stability = 10.0,
      difficulty = 5.0,
      reps = 2,
      lapses = 0,
      phase = CardPhase.REVIEW,
      step = 0,
    )
  }

  private fun dataMove(from: PositionKey, san: String, to: PositionKey, createdAt: Instant) =
    DataMove(
      origin = from,
      destination = to,
      move = san,
      isGood = true,
      createdAt = createdAt,
      updatedAt = createdAt,
    )

  private fun dataNode(
    key: PositionKey,
    depth: Int,
    incoming: List<DataMove>,
    outgoing: List<DataMove>,
    createdAt: Instant = DateUtil.now(),
    hasGoodOutgoing: Boolean = true,
  ) =
    DataNode(
      positionKey = key,
      previousAndNextMoves = PreviousAndNextMoves(incoming, outgoing),
      cardState = CardStateFactory.new(),
      depth = depth,
      hasGoodOutgoing = hasGoodOutgoing,
      createdAt = createdAt,
    )

  private fun reviewCard(due: Instant): CardState =
    CardState(
      dueDate = due,
      lastReview = due - 1.days,
      firstReview = due - 1.days,
      stability = 1.0,
      difficulty = 5.0,
      reps = 1,
      lapses = 0,
      phase = CardPhase.REVIEW,
      step = 0,
    )

  private fun learningCard(due: Instant): CardState =
    CardState(
      dueDate = due,
      lastReview = due,
      firstReview = due,
      stability = 1.0,
      difficulty = 5.0,
      reps = 1,
      lapses = 0,
      phase = CardPhase.RELEARNING,
      step = 0,
    )
}
