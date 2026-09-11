package proj.memorchess.axl.ui.components.navigation

import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.waitUntilAtLeastOneExists
import io.kotest.matchers.collections.shouldBeEmpty
import kotlin.test.Test
import kotlinx.coroutines.awaitCancellation
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import proj.memorchess.axl.core.data.DailyActivityRecord
import proj.memorchess.axl.core.data.DailyActivityStore
import proj.memorchess.axl.core.data.InMemoryDatabaseQueryManager
import proj.memorchess.axl.core.date.DateUtil
import proj.memorchess.axl.core.graph.TrainingScheduler
import proj.memorchess.axl.core.scheduling.Fsrs6SchedulingAlgorithm
import proj.memorchess.axl.core.streak.StreakTracker
import proj.memorchess.axl.test_util.InMemoryDailyActivityStore
import proj.memorchess.axl.test_util.TestDatabases
import proj.memorchess.axl.test_util.testTreeStore
import proj.memorchess.axl.ui.setKineticContent

/**
 * Pins [KineticDesktopRail]'s loading/loaded contract and its week-strip wiring.
 *
 * The streak card's own digit/badge/target-boundary battery already lives on [TestKineticSideBar]
 * ([StreakCard] is shared), so this class only checks that [KineticDesktopRail] wires it and
 * [proj.memorchess.axl.ui.components.today.WeekStrip] correctly, plus the nav-row wiring already
 * pinned via [KineticSideNav].
 */
@OptIn(ExperimentalTestApi::class)
internal class TestKineticDesktopRail {

  private val items = NavigationBarItemContent.entries.sortedBy { it.index }

  /** Mirrors `TestKineticSideBar`'s never-returning store, used to pin the loading branch. */
  private class NeverReturningDailyActivityStore : DailyActivityStore {
    override suspend fun getRecord(date: LocalDate): DailyActivityRecord = awaitCancellation()

    override suspend fun putRecord(record: DailyActivityRecord): Unit = awaitCancellation()

    override suspend fun eraseAll(): Unit = awaitCancellation()
  }

  /** Mirrors `TestKineticSideBar.seedStreak`: a non-zero streak lives on yesterday's record. */
  private suspend fun seedStreak(store: InMemoryDailyActivityStore, streak: Int, done: Int) {
    val today = DateUtil.today()
    if (streak > 0) {
      store.putRecord(
        DailyActivityRecord(
          date = today.minus(1, DateTimeUnit.DAY),
          cardsReviewed = 1,
          isActive = true,
          streakLength = streak,
        )
      )
    }
    if (done > 0) {
      store.putRecord(
        DailyActivityRecord(
          date = today,
          cardsReviewed = done,
          isActive = true,
          streakLength = streak,
        )
      )
    }
  }

  private suspend fun dbWithDueCards(count: Int): InMemoryDatabaseQueryManager {
    val database = TestDatabases.empty()
    if (count > 0) {
      val moves = listOf("e4", "e5", "Nf3").take(count)
      database.insertNodes(*TestDatabases.convertStringMovesToNodes(moves).toTypedArray())
    }
    return database
  }

  private fun schedulerOver(database: InMemoryDatabaseQueryManager) =
    TrainingScheduler(
      database,
      testTreeStore(database),
      Fsrs6SchedulingAlgorithm(),
      maxNewMovesPerDay = { Int.MAX_VALUE },
      maxTotalMovesPerDay = { Int.MAX_VALUE },
    )

  private fun rowTag(item: NavigationBarItemContent) = "desktop_rail_${item.name}"

  private fun ComposeUiTest.setDesktopRail(
    streakTracker: StreakTracker,
    scheduler: TrainingScheduler,
    currentRoute: String = NavigationBarItemContent.Training.destination.getLabel(),
  ) {
    setKineticContent {
      KineticDesktopRail(
        items = items,
        currentRoute = currentRoute,
        onSelect = {},
        itemModifier = { Modifier.testTag(rowTag(it)) },
        streakTracker = streakTracker,
        scheduler = scheduler,
      )
    }
  }

  private fun ComposeUiTest.digitTexts(matcher: SemanticsMatcher): List<String> =
    onAllNodes(matcher)
      .fetchSemanticsNodes()
      .flatMap { it.config.getOrNull(SemanticsProperties.Text).orEmpty() }
      .map { it.text }
      .filter { text -> text.any { it.isDigit() } }

  @Test
  fun loadedStateRendersStreakAndWeekStrip() = runComposeUiTest {
    val store = InMemoryDailyActivityStore()
    seedStreak(store, streak = 3, done = 1)

    setDesktopRail(StreakTracker(store), schedulerOver(dbWithDueCards(0)))

    waitUntilAtLeastOneExists(hasText("3"))
    onNodeWithText("3").assertIsDisplayed()
    waitUntilAtLeastOneExists(hasTestTag("rail_week_strip"))
    (1..7).forEach { isoIndex -> onNodeWithTag("rail_week_cell_$isoIndex").assertIsDisplayed() }
  }

  @Test
  fun loadingRendersNoDigitsAndNoWeekStrip() = runComposeUiTest {
    setDesktopRail(
      StreakTracker(NeverReturningDailyActivityStore()),
      schedulerOver(dbWithDueCards(0)),
    )

    waitUntilAtLeastOneExists(hasTestTag(rowTag(NavigationBarItemContent.Training)))
    digitTexts(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text)).shouldBeEmpty()
    onNodeWithTag("rail_week_strip").assertDoesNotExist()
  }

  @Test
  fun activeRowIsSelected() = runComposeUiTest {
    setDesktopRail(StreakTracker(InMemoryDailyActivityStore()), schedulerOver(dbWithDueCards(0)))

    onNodeWithTag(rowTag(NavigationBarItemContent.Training)).assertIsSelected()
    items
      .filter { it != NavigationBarItemContent.Training }
      .forEach { onNodeWithTag(rowTag(it)).assertIsNotSelected() }
  }
}
