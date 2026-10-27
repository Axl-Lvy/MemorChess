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
import proj.memorchess.axl.core.graph.RepertoireTagStore
import proj.memorchess.axl.core.graph.TrainingScheduler
import proj.memorchess.axl.core.scheduling.CardPhase
import proj.memorchess.axl.core.scheduling.CardStateFactory
import proj.memorchess.axl.core.scheduling.Fsrs6SchedulingAlgorithm
import proj.memorchess.axl.core.streak.StreakTracker
import proj.memorchess.axl.test_util.InMemoryDailyActivityStore
import proj.memorchess.axl.test_util.TestDatabases
import proj.memorchess.axl.test_util.TestWithKoin
import proj.memorchess.axl.test_util.testRepertoireTagStore
import proj.memorchess.axl.test_util.testTreeStore
import proj.memorchess.axl.ui.components.today.WeekStrip
import proj.memorchess.axl.ui.pages.navigation.Route
import proj.memorchess.axl.ui.setTestContent
import proj.memorchess.axl.ui.waitUntilNodeExists

/** A [DailyActivityStore] whose reads never complete, so [Today] stays in its loading branch. */
private class NeverReturningDailyActivityStore : DailyActivityStore {
  override suspend fun getRecord(date: LocalDate): DailyActivityRecord = awaitCancellation()

  override suspend fun putRecord(record: DailyActivityRecord): Unit = awaitCancellation()

  override suspend fun eraseAll(): Unit = awaitCancellation()
}

/**
 * Counts are asserted through test tags rather than resource text, whose locale CI does not fix.
 * [TestWithKoin] backs the CTA's reduce-motion read on first press.
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

  /** An in memory database holding [count] brand new, hence pending, cards. */
  private suspend fun dbWithDueCards(count: Int): InMemoryDatabaseQueryManager {
    val database = TestDatabases.empty()
    if (count > 0) {
      val moves = listOf("e4", "e5", "Nf3", "Nc6").take(count)
      database.insertNodes(*TestDatabases.convertStringMovesToNodes(moves).toTypedArray())
    }
    return database
  }

  private fun schedulerOver(database: InMemoryDatabaseQueryManager) =
    TrainingScheduler(
      database,
      testTreeStore(database),
      testRepertoireTagStore(database),
      Fsrs6SchedulingAlgorithm(),
      maxNewMovesPerDay = { Int.MAX_VALUE },
      maxTotalMovesPerDay = { Int.MAX_VALUE },
    )

  private suspend fun ComposeUiTest.setToday(
    streakTracker: StreakTracker,
    scheduler: TrainingScheduler,
    tagStore: RepertoireTagStore,
  ) {
    setTestContent {
      InitializeApp {
        Today(streakTracker = streakTracker, scheduler = scheduler, tagStore = tagStore)
      }
    }
  }

  @Test
  fun zeroDayStreakRendersTheBadge() = runTestFromSetup {
    val store = InMemoryDailyActivityStore()
    val database = dbWithDueCards(0)
    setToday(StreakTracker(store), schedulerOver(database), testRepertoireTagStore(database))

    waitUntilNodeExists(hasTestTag("today_streak_badge"))
    onNodeWithTag("today_streak_badge").assertIsDisplayed()
    waitUntilNodeExists(hasText("0"))
    onNodeWithText("0").assertIsDisplayed()
  }

  @Test
  fun everyWeekStripCellRenders() = runTestFromSetup {
    val store = InMemoryDailyActivityStore()
    val database = dbWithDueCards(0)
    setToday(StreakTracker(store), schedulerOver(database), testRepertoireTagStore(database))

    waitUntilNodeExists(hasTestTag("today_week_strip"))
    (1..7).forEach { isoIndex -> onNodeWithTag("today_week_cell_$isoIndex").assertExists() }
  }

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

    setToday(streakTracker, scheduler, testRepertoireTagStore(database))

    waitUntilNodeExists(hasTestTag("today_goal_ring"))
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
    setToday(StreakTracker(store), schedulerOver(database), testRepertoireTagStore(database))

    waitUntilNodeExists(hasTestTag("today_goal_ring"))
    onNodeWithTag("today_goal_ring").assertRangeInfoEquals(ProgressBarRangeInfo(0f, 0f..1f))
    onNodeWithTag("today_goal_label_done").assertIsDisplayed()
    onNodeWithTag("today_goal_label_progress").assertDoesNotExist()
  }

  @Test
  fun noRepertoiresInstalledRendersTheEmptyPickUpCard() = runTestFromSetup {
    val store = InMemoryDailyActivityStore()
    val database = dbWithDueCards(0)
    setToday(
      StreakTracker(store),
      schedulerOver(database),
      testRepertoireTagStore(TestDatabases.empty()),
    )

    waitUntilNodeExists(hasTestTag("today_pickup_empty"))
    onNodeWithTag("today_pickup_card").assertDoesNotExist()
  }

  @Test
  fun aRepertoireWithAReviewedPositionRendersThePickUpCard() = runTestFromSetup {
    val store = InMemoryDailyActivityStore()
    val database = dbWithDueCards(0)
    val treeStore = testTreeStore(database)
    val tagStore = testRepertoireTagStore(database)
    tagStore.register("italian-game", "Italian Game", RepertoireColor.WHITE)
    val destination = PositionKey("posA b K")
    treeStore.addMove(PositionKey.START_POSITION, "e4", destination, isGood = true, fromDepth = 0)
    tagStore.tag(PositionKey.START_POSITION, destination, "italian-game")
    treeStore.updateCardState(
      PositionKey.START_POSITION,
      CardStateFactory.new().copy(phase = CardPhase.REVIEW, lastReview = DateUtil.now()),
    )

    setToday(StreakTracker(store), schedulerOver(database), tagStore)

    waitUntilNodeExists(hasTestTag("today_pickup_card"))
    onNodeWithTag("today_pickup_empty").assertDoesNotExist()
    onNode(hasText("Italian Game")).assertIsDisplayed()
  }

  @Test
  fun ctaAtOnePendingUsesTheSingularBranch() = runTestFromSetup {
    val store = InMemoryDailyActivityStore()
    val database = dbWithDueCards(1)
    schedulerOver(database).pendingCount() shouldBe 1
    setToday(StreakTracker(store), schedulerOver(database), testRepertoireTagStore(database))

    waitUntilNodeExists(hasTestTag("today_cta"))
    onNodeWithTag("today_cta_pending_count_1", useUnmergedTree = true).assertIsDisplayed()
  }

  @Test
  fun ctaAtTwoPendingUsesThePluralBranch() = runTestFromSetup {
    val store = InMemoryDailyActivityStore()
    val database = dbWithDueCards(2)
    schedulerOver(database).pendingCount() shouldBe 2
    setToday(StreakTracker(store), schedulerOver(database), testRepertoireTagStore(database))

    waitUntilNodeExists(hasTestTag("today_cta"))
    onNodeWithTag("today_cta_pending_count_2", useUnmergedTree = true).assertIsDisplayed()
  }

  // A chosen todayIsoIndex keeps the classification independent of the day the suite runs on.
  private suspend fun ComposeUiTest.setWeekStrip(
    week: List<Boolean>,
    todayIsoIndex: Int,
    tagPrefix: String = "today_week",
  ) {
    setTestContent {
      InitializeApp { WeekStrip(week = week, todayIsoIndex = todayIsoIndex, tagPrefix = tagPrefix) }
    }
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
    // isoIndex 1 is Monday, so every other day is in the future.
    setWeekStrip(week = weekOf(), todayIsoIndex = 1)

    (2..7).forEach { isoIndex ->
      onNodeWithTag("today_week_cell_$isoIndex").assert(hasWeekCellState("FUTURE"))
    }
    onNodeWithTag("today_week_cell_1").assert(hasWeekCellState("TODAY"))
  }

  @Test
  fun sundayBoundaryHasNoFutureDayLeft() = runTestFromSetup {
    // isoIndex 7 is Sunday, so every other day is in the past.
    setWeekStrip(week = weekOf(7), todayIsoIndex = 7)

    (1..6).forEach { isoIndex ->
      onNodeWithTag("today_week_cell_$isoIndex").assert(hasWeekCellState("MISSED"))
    }
    onNodeWithTag("today_week_cell_7").assert(hasWeekCellState("TODAY"))
  }

  @Test
  fun customTagPrefixRendersTagsUnderThatPrefix() = runTestFromSetup {
    // The desktop rail renders its own WeekStrip beside Today's, so their tags must not collide.
    setWeekStrip(week = weekOf(2), todayIsoIndex = 4, tagPrefix = "rail_week")

    onNodeWithTag("rail_week_strip").assertExists()
    onNodeWithTag("rail_week_cell_2").assert(hasWeekCellState("DONE"))
    onNodeWithTag("today_week_strip").assertDoesNotExist()
  }

  @Test
  fun loadingRendersNoContent() = runTestFromSetup {
    val database = dbWithDueCards(3)
    setToday(
      StreakTracker(NeverReturningDailyActivityStore()),
      schedulerOver(database),
      testRepertoireTagStore(database),
    )

    onNodeWithTag("today_streak_badge").assertDoesNotExist()
    onNodeWithTag("today_cta").assertDoesNotExist()
    onNodeWithTag("today_pickup_card").assertDoesNotExist()
    onNodeWithTag("today_pickup_empty").assertDoesNotExist()
  }
}
