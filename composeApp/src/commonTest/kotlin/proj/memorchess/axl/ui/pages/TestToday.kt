package proj.memorchess.axl.ui.pages

import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertRangeInfoEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.waitUntilAtLeastOneExists
import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlinx.coroutines.awaitCancellation
import kotlinx.datetime.LocalDate
import proj.memorchess.axl.core.data.DailyActivityRecord
import proj.memorchess.axl.core.data.DailyActivityStore
import proj.memorchess.axl.core.data.InMemoryDatabaseQueryManager
import proj.memorchess.axl.core.data.PositionKey
import proj.memorchess.axl.core.data.repertoire.RepertoireColor
import proj.memorchess.axl.core.date.DateUtil
import proj.memorchess.axl.core.graph.TrainingScheduler
import proj.memorchess.axl.core.graph.TreeStore
import proj.memorchess.axl.core.scheduling.CardPhase
import proj.memorchess.axl.core.scheduling.CardStateFactory
import proj.memorchess.axl.core.scheduling.Fsrs6SchedulingAlgorithm
import proj.memorchess.axl.core.streak.StreakTracker
import proj.memorchess.axl.test_util.InMemoryDailyActivityStore
import proj.memorchess.axl.test_util.TestDatabases
import proj.memorchess.axl.test_util.TestWithKoin
import proj.memorchess.axl.test_util.testTreeStore
import proj.memorchess.axl.ui.pages.navigation.Route

/**
 * A [DailyActivityStore] whose reads never complete, so `produceState` stays unresolved. Used to
 * observe [Today]'s loading branch deterministically, mirroring `TestKineticSideBar`'s
 * `NeverReturningDailyActivityStore`.
 */
private class NeverReturningDailyActivityStore : DailyActivityStore {
  override suspend fun getRecord(date: LocalDate): DailyActivityRecord = awaitCancellation()

  override suspend fun putRecord(record: DailyActivityRecord): Unit = awaitCancellation()

  override suspend fun eraseAll(): Unit = awaitCancellation()
}

/**
 * Pins [Today]'s stats-loading contract, the goal-ring target branches, the CTA plural boundary,
 * and the pick-up card's loading/empty/populated states.
 *
 * Every dependency besides Koin is built by hand and passed explicitly, matching
 * `TestKineticSideBar`'s style. [TestWithKoin] is needed here (unlike `TestKineticSideBar`) because
 * the CTA is a real `KineticButton`, whose press animation reads the reduce-motion setting through
 * Koin on first press. That is the same reason `TestKineticBottomNav` keeps its whole class on
 * `TestWithKoin` rather than splitting it.
 *
 * The goal ring and CTA plural assertions go through test tags carrying the underlying number
 * (`today_goal_label_done`/`today_goal_label_progress`, `today_cta_pending_count_N`) rather than
 * matching resource text. The resource locale is not fixed on CI, so a text assertion would be
 * flaky by construction, matching `TestKineticBottomNav`'s own rule.
 */
@OptIn(ExperimentalTestApi::class)
class TestToday : TestWithKoin() {

  private fun runTestFromSetup(block: suspend ComposeUiTest.() -> Unit) = runComposeUiTest {
    koinSetUp()
    try {
      block()
    } finally {
      koinTearDown()
    }
  }

  /**
   * An in memory database holding [count] brand new cards, i.e. [count] pending training entries.
   */
  private suspend fun dbWithDueCards(count: Int): InMemoryDatabaseQueryManager {
    val database = TestDatabases.empty()
    if (count > 0) {
      val moves = listOf("e4", "e5", "Nf3", "Nc6").take(count)
      database.insertNodes(*TestDatabases.convertStringMovesToNodes(moves).toTypedArray())
    }
    return database
  }

  private fun schedulerOver(database: InMemoryDatabaseQueryManager) =
    TrainingScheduler(database, testTreeStore(database), Fsrs6SchedulingAlgorithm())

  private fun ComposeUiTest.setToday(
    streakTracker: StreakTracker,
    scheduler: TrainingScheduler,
    treeStore: TreeStore,
  ) {
    setContent {
      InitializeApp {
        Today(streakTracker = streakTracker, scheduler = scheduler, treeStore = treeStore)
      }
    }
  }

  // STREAK BADGE

  @Test
  fun zeroDayStreakRendersTheBadge() = runTestFromSetup {
    val store = InMemoryDailyActivityStore()
    val database = dbWithDueCards(0)
    setToday(StreakTracker(store), schedulerOver(database), testTreeStore(database))

    waitUntilAtLeastOneExists(hasTestTag("today_streak_badge"))
    onNodeWithTag("today_streak_badge").assertIsDisplayed()
    waitUntilAtLeastOneExists(hasText("0"))
    onNodeWithText("0").assertIsDisplayed()
  }

  // WEEK STRIP — every one of the seven cells renders

  @Test
  fun everyWeekStripCellRenders() = runTestFromSetup {
    val store = InMemoryDailyActivityStore()
    val database = dbWithDueCards(0)
    setToday(StreakTracker(store), schedulerOver(database), testTreeStore(database))

    waitUntilAtLeastOneExists(hasTestTag("today_week_strip"))
    (1..7).forEach { isoIndex -> onNodeWithTag("today_week_cell_$isoIndex").assertExists() }
  }

  // GOAL RING — target == 0 vs target > 0, goal already met

  @Test
  fun zeroCardsDueAndGoalAlreadyMetReportsFullRingAndATappableCta() = runTestFromSetup {
    val store = InMemoryDailyActivityStore()
    val today = DateUtil.today()
    store.putRecord(
      DailyActivityRecord(date = today, cardsReviewed = 2, isActive = true, streakLength = 1)
    )
    val database = dbWithDueCards(0)
    val streakTracker = StreakTracker(store)
    val scheduler = schedulerOver(database)
    scheduler.pendingCount() shouldBe 0

    setToday(streakTracker, scheduler, testTreeStore(database))

    waitUntilAtLeastOneExists(hasTestTag("today_goal_ring"))
    onNodeWithTag("today_goal_ring").assertRangeInfoEquals(ProgressBarRangeInfo(1f, 0f..1f))
    onNodeWithTag("today_cta").assertIsDisplayed()
    // KineticButton merges its subtree's semantics, so the count tag on the label inside it is
    // only visible via the unmerged tree.
    onNodeWithTag("today_cta_pending_count_0", useUnmergedTree = true).assertIsDisplayed()

    onNodeWithTag("today_cta").performClick()

    navigator.lastRoute shouldBe Route.TrainingRoute.DEFAULT
  }

  @Test
  fun zeroDoneAndZeroDueDropsTheTargetHalfOfTheLabelAndReportsAnEmptyRing() = runTestFromSetup {
    val store = InMemoryDailyActivityStore()
    val database = dbWithDueCards(0)
    setToday(StreakTracker(store), schedulerOver(database), testTreeStore(database))

    waitUntilAtLeastOneExists(hasTestTag("today_goal_ring"))
    onNodeWithTag("today_goal_ring").assertRangeInfoEquals(ProgressBarRangeInfo(0f, 0f..1f))
    onNodeWithTag("today_goal_label_done").assertIsDisplayed()
    onNodeWithTag("today_goal_label_progress").assertDoesNotExist()
  }

  // PICK UP CARD — no repertoires installed vs. a populated repertoire

  @Test
  fun noRepertoiresInstalledRendersTheEmptyPickUpCard() = runTestFromSetup {
    val store = InMemoryDailyActivityStore()
    val database = dbWithDueCards(0)
    val treeStore = testTreeStore(TestDatabases.empty())
    setToday(StreakTracker(store), schedulerOver(database), treeStore)

    waitUntilAtLeastOneExists(hasTestTag("today_pickup_empty"))
    onNodeWithTag("today_pickup_card").assertDoesNotExist()
  }

  @Test
  fun aRepertoireWithAReviewedPositionRendersThePickUpCard() = runTestFromSetup {
    val store = InMemoryDailyActivityStore()
    val database = dbWithDueCards(0)
    val treeStore = testTreeStore(database)
    treeStore.registerRepertoire("italian-game", "Italian Game", RepertoireColor.WHITE)
    val destination = PositionKey("posA b K")
    treeStore.addMove(PositionKey.START_POSITION, "e4", destination, isGood = true, fromDepth = 0)
    treeStore.tagEdge(PositionKey.START_POSITION, destination, "italian-game")
    treeStore.updateCardState(
      PositionKey.START_POSITION,
      CardStateFactory.new().copy(phase = CardPhase.REVIEW, lastReview = DateUtil.now()),
    )

    setToday(StreakTracker(store), schedulerOver(database), treeStore)

    waitUntilAtLeastOneExists(hasTestTag("today_pickup_card"))
    onNodeWithTag("today_pickup_empty").assertDoesNotExist()
    onNode(hasText("Italian Game")).assertIsDisplayed()
  }

  // CTA PLURAL BOUNDARY

  @Test
  fun ctaAtOnePendingUsesTheSingularBranch() = runTestFromSetup {
    val store = InMemoryDailyActivityStore()
    val database = dbWithDueCards(1)
    schedulerOver(database).pendingCount() shouldBe 1
    setToday(StreakTracker(store), schedulerOver(database), testTreeStore(database))

    waitUntilAtLeastOneExists(hasTestTag("today_cta"))
    onNodeWithTag("today_cta_pending_count_1", useUnmergedTree = true).assertIsDisplayed()
  }

  @Test
  fun ctaAtTwoPendingUsesThePluralBranch() = runTestFromSetup {
    val store = InMemoryDailyActivityStore()
    val database = dbWithDueCards(2)
    schedulerOver(database).pendingCount() shouldBe 2
    setToday(StreakTracker(store), schedulerOver(database), testTreeStore(database))

    waitUntilAtLeastOneExists(hasTestTag("today_cta"))
    onNodeWithTag("today_cta_pending_count_2", useUnmergedTree = true).assertIsDisplayed()
  }

  // WEEK CELL CLASSIFICATION — every arm, independent of which real day the suite runs on.
  // Drives WeekStrip (an internal, testable seam) directly with a chosen todayIsoIndex, rather
  // than calling the page-private classifyWeekCell, and reads the classification back off each
  // cell's semantics state description instead of its (locale dependent) background colour.

  private fun ComposeUiTest.setWeekStrip(week: List<Boolean>, todayIsoIndex: Int) {
    setContent { InitializeApp { WeekStrip(week = week, todayIsoIndex = todayIsoIndex) } }
  }

  /** A 7 day week with every isoIndex in [activeIsoIndices] marked active, the rest inactive. */
  private fun weekOf(vararg activeIsoIndices: Int): List<Boolean> {
    val active = activeIsoIndices.toSet()
    return (1..7).map { it in active }
  }

  private fun hasWeekCellState(state: String) =
    SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, state)

  @Test
  fun aPastActiveDayIsDone() = runTestFromSetup {
    setWeekStrip(week = weekOf(2), todayIsoIndex = 4)

    onNodeWithTag("today_week_cell_2").assert(hasWeekCellState("DONE"))
  }

  @Test
  fun aPastInactiveDayIsMissed() = runTestFromSetup {
    setWeekStrip(week = weekOf(), todayIsoIndex = 4)

    onNodeWithTag("today_week_cell_2").assert(hasWeekCellState("MISSED"))
  }

  @Test
  fun todayIsAlwaysTodayEvenWhenAlreadyActive() = runTestFromSetup {
    setWeekStrip(week = weekOf(4), todayIsoIndex = 4)

    onNodeWithTag("today_week_cell_4").assert(hasWeekCellState("TODAY"))
  }

  @Test
  fun todayIsAlwaysTodayWhenNotYetActive() = runTestFromSetup {
    setWeekStrip(week = weekOf(), todayIsoIndex = 4)

    onNodeWithTag("today_week_cell_4").assert(hasWeekCellState("TODAY"))
  }

  @Test
  fun aFutureActiveDayIsStillFuture() = runTestFromSetup {
    setWeekStrip(week = weekOf(6), todayIsoIndex = 4)

    onNodeWithTag("today_week_cell_6").assert(hasWeekCellState("FUTURE"))
  }

  @Test
  fun aFutureInactiveDayIsFuture() = runTestFromSetup {
    setWeekStrip(week = weekOf(), todayIsoIndex = 4)

    onNodeWithTag("today_week_cell_6").assert(hasWeekCellState("FUTURE"))
  }

  @Test
  fun mondayBoundaryHasNoPastDayLeftFuture() = runTestFromSetup {
    // Today is Monday (isoIndex 1): every other day of the week is FUTURE, none can be
    // DONE/MISSED, the boundary the spec calls out explicitly.
    setWeekStrip(week = weekOf(), todayIsoIndex = 1)

    (2..7).forEach { isoIndex ->
      onNodeWithTag("today_week_cell_$isoIndex").assert(hasWeekCellState("FUTURE"))
    }
    onNodeWithTag("today_week_cell_1").assert(hasWeekCellState("TODAY"))
  }

  @Test
  fun sundayBoundaryHasNoFutureDayLeft() = runTestFromSetup {
    // Today is Sunday (isoIndex 7): every other day is in the past, none can be FUTURE.
    setWeekStrip(week = weekOf(7), todayIsoIndex = 7)

    (1..6).forEach { isoIndex ->
      onNodeWithTag("today_week_cell_$isoIndex").assert(hasWeekCellState("MISSED"))
    }
    onNodeWithTag("today_week_cell_7").assert(hasWeekCellState("TODAY"))
  }

  // LOADING CONTRACT

  @Test
  fun loadingRendersNoContent() = runTestFromSetup {
    val database = dbWithDueCards(3)
    setToday(
      StreakTracker(NeverReturningDailyActivityStore()),
      schedulerOver(database),
      testTreeStore(database),
    )

    onNodeWithTag("today_streak_badge").assertDoesNotExist()
    onNodeWithTag("today_cta").assertDoesNotExist()
    onNodeWithTag("today_pickup_card").assertDoesNotExist()
    onNodeWithTag("today_pickup_empty").assertDoesNotExist()
  }
}
