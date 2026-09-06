package proj.memorchess.axl.ui.components.navigation

import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.waitUntilAtLeastOneExists
import androidx.compose.ui.text.TextLayoutResult
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
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
import proj.memorchess.axl.core.scheduling.ReviewGrade
import proj.memorchess.axl.core.streak.StreakTracker
import proj.memorchess.axl.test_util.InMemoryDailyActivityStore
import proj.memorchess.axl.test_util.TestDatabases
import proj.memorchess.axl.test_util.testTreeStore
import proj.memorchess.axl.ui.pages.navigation.Route
import proj.memorchess.axl.ui.setKineticContent

/** Per-row test tag, mirroring the shape of the caller-supplied production tags. */
private fun rowTag(item: NavigationBarItemContent) = "rail_${item.name}"

/**
 * A [DailyActivityStore] whose reads never complete, so `produceState` stays unresolved. Used to
 * observe the rail's loading branch deterministically.
 */
private class NeverReturningDailyActivityStore : DailyActivityStore {
  override suspend fun getRecord(date: LocalDate): DailyActivityRecord = awaitCancellation()

  override suspend fun putRecord(record: DailyActivityRecord): Unit = awaitCancellation()

  override suspend fun eraseAll(): Unit = awaitCancellation()
}

/**
 * Pins [KineticSideBar]'s streak card, its badge branch and the per-row test-tag contract.
 *
 * No Koin container: both dependencies are built by hand and passed explicitly, which also proves
 * the `koinInject()` defaults really are defaults.
 */
@OptIn(ExperimentalTestApi::class)
internal class TestKineticSideBar {

  private val items = NavigationBarItemContent.entries.sortedBy { it.index }

  /**
   * Seeds [store] so that [StreakTracker.streakDays] reports [streak] and
   * [StreakTracker.cardsCompletedToday] reports [done].
   *
   * A non-zero streak with nothing done yet lives on YESTERDAY's active record — that is exactly
   * what `streakDays()` falls back to, and the normal state of the app first thing in the morning.
   * Writing records directly rather than calling `recordReview()` N times is deliberate: a
   * 99999-day streak must not cost 99999 writes, and `recordReview()` cannot produce a multi-day
   * streak without walking every intervening day.
   *
   * Callers keep `(streak, done)` self-consistent: `done > 0` implies `streak >= 1`.
   */
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

  /**
   * An in memory database holding [count] brand new cards, i.e. [count] pending training entries.
   */
  private suspend fun dbWithDueCards(count: Int): InMemoryDatabaseQueryManager {
    val database = TestDatabases.empty()
    if (count > 0) {
      val moves = listOf("e4", "e5", "Nf3").take(count)
      database.insertNodes(*TestDatabases.convertStringMovesToNodes(moves).toTypedArray())
    }
    return database
  }

  private fun schedulerOver(database: InMemoryDatabaseQueryManager) =
    TrainingScheduler(database, testTreeStore(database), Fsrs6SchedulingAlgorithm())

  private fun ComposeUiTest.setRail(
    streakTracker: StreakTracker,
    scheduler: TrainingScheduler,
    currentRoute: String = Route.TrainingRoute.getLabel(),
    selected: MutableList<NavigationBarItemContent> = mutableListOf(),
  ) {
    setKineticContent {
      KineticSideBar(
        items = items,
        currentRoute = currentRoute,
        onSelect = { selected += it },
        itemModifier = { Modifier.testTag(rowTag(it)) },
        streakTracker = streakTracker,
        scheduler = scheduler,
      )
    }
  }

  /** Every rendered text matching [matcher] that contains at least one digit. */
  private fun ComposeUiTest.digitTexts(matcher: SemanticsMatcher): List<String> =
    onAllNodes(matcher)
      .fetchSemanticsNodes()
      .flatMap { it.config.getOrNull(SemanticsProperties.Text).orEmpty() }
      .map { it.text }
      .filter { text -> text.any { it.isDigit() } }

  /**
   * Every digit-bearing text on the row tagged [tag]. A nav row merges its descendants (it is
   * clickable), so the label and the badge both surface as texts on the row's own node.
   */
  private fun ComposeUiTest.rowDigitTexts(tag: String): List<String> =
    onNodeWithTag(tag)
      .fetchSemanticsNode()
      .config
      .getOrNull(SemanticsProperties.Text)
      .orEmpty()
      .map { it.text }
      .filter { text -> text.any { it.isDigit() } }

  /**
   * Asserts the node whose text matches [text] laid out on a single line, i.e. the rail gave it its
   * full intrinsic width instead of squeezing it into a wrap.
   *
   * `TextLayoutResult.hasVisualOverflow` is deliberately not used: in this harness it reports
   * `true` even for a one-character line that plainly fits, so it carries no signal. Wrapping is
   * the failure mode a five-digit streak actually has.
   */
  private fun ComposeUiTest.assertLaidOutOnOneLine(text: String, substring: Boolean = false) {
    val results = mutableListOf<TextLayoutResult>()
    onNodeWithText(text, substring = substring)
      .fetchSemanticsNode()
      .config[SemanticsActions.GetTextLayoutResult]
      .action
      ?.invoke(results)
    results.first().lineCount shouldBe 1
  }

  // STREAK NUMBER

  @Test
  fun streakZeroRenders() = runComposeUiTest {
    val store = InMemoryDailyActivityStore()
    seedStreak(store, streak = 0, done = 0)

    setRail(StreakTracker(store), schedulerOver(dbWithDueCards(0)))

    waitUntilAtLeastOneExists(hasText("0"))
    onNodeWithText("0").assertIsDisplayed()
  }

  @Test
  fun streakOneRenders() = runComposeUiTest {
    val store = InMemoryDailyActivityStore()
    seedStreak(store, streak = 1, done = 0)

    setRail(StreakTracker(store), schedulerOver(dbWithDueCards(0)))

    waitUntilAtLeastOneExists(hasText("1"))
    onNodeWithText("1").assertIsDisplayed()
  }

  @Test
  fun largeStreakRenders() = runComposeUiTest {
    val store = InMemoryDailyActivityStore()
    seedStreak(store, streak = 99999, done = 0)

    setRail(StreakTracker(store), schedulerOver(dbWithDueCards(0)))

    waitUntilAtLeastOneExists(hasText("99999"))
    onNodeWithText("99999").assertIsDisplayed()
    assertLaidOutOnOneLine("99999")
  }

  // TODAY LINE — the `target == 0` vs `target > 0` branch

  @Test
  fun targetZeroHidesTheSlash() = runComposeUiTest {
    val store = InMemoryDailyActivityStore()
    seedStreak(store, streak = 0, done = 0)
    val database = dbWithDueCards(0)
    schedulerOver(database).pendingCount() shouldBe 0

    setRail(StreakTracker(store), schedulerOver(database))

    waitUntilAtLeastOneExists(hasText("0"))
    onNode(hasText("0/", substring = true)).assertDoesNotExist()
  }

  @Test
  fun targetOneFromDoneSide() = runComposeUiTest {
    val store = InMemoryDailyActivityStore()
    seedStreak(store, streak = 1, done = 1)
    val database = dbWithDueCards(0)
    schedulerOver(database).pendingCount() shouldBe 0

    setRail(StreakTracker(store), schedulerOver(database))

    waitUntilAtLeastOneExists(hasText("1/1", substring = true))
  }

  @Test
  fun targetOneFromDueSide() = runComposeUiTest {
    val store = InMemoryDailyActivityStore()
    seedStreak(store, streak = 0, done = 0)
    val database = dbWithDueCards(1)
    schedulerOver(database).pendingCount() shouldBe 1

    setRail(StreakTracker(store), schedulerOver(database))

    waitUntilAtLeastOneExists(hasText("0/1", substring = true))
  }

  @Test
  fun largeDoneAndTarget() = runComposeUiTest {
    val store = InMemoryDailyActivityStore()
    seedStreak(store, streak = 99999, done = 99999)
    val database = dbWithDueCards(1)
    schedulerOver(database).pendingCount() shouldBe 1

    setRail(StreakTracker(store), schedulerOver(database))

    // The headline number must never wrap; the secondary line is allowed to, and does at a
    // six-digit target. Nothing is clipped either way — the rail scrolls.
    waitUntilAtLeastOneExists(hasText("99999/100000", substring = true))
    assertLaidOutOnOneLine("99999")
  }

  @Test
  fun loadingRendersNoDigits() = runComposeUiTest {
    setRail(StreakTracker(NeverReturningDailyActivityStore()), schedulerOver(dbWithDueCards(3)))

    waitUntilAtLeastOneExists(hasTestTag(rowTag(NavigationBarItemContent.Training)))
    // Nothing in the rail carries a digit until the snapshot resolves — not even a placeholder "0".
    digitTexts(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text)).shouldBeEmpty()
  }

  @Test
  fun targetDoesNotDoubleCountAGradedInSessionCard() = runComposeUiTest {
    // A single new card, graded GOOD once, lands in CardPhase.LEARNING step 1 (the default Fsrs6
    // ladder is [1.minutes, 10.minutes], so GOOD advances one step without graduating). It is now
    // both "done" (its first review of the day already happened) and "in session" (still mid
    // learning). The target must count it once, not twice.
    val store = InMemoryDailyActivityStore()
    val streakTracker = StreakTracker(store)
    val database = dbWithDueCards(1)
    val scheduler =
      TrainingScheduler(
        database,
        testTreeStore(database),
        Fsrs6SchedulingAlgorithm(),
        streakTracker = streakTracker,
      )
    val entry = scheduler.nextDue()
    checkNotNull(entry) { "dbWithDueCards(1) must seed one trainable position" }
    scheduler.grade(entry.positionKey, ReviewGrade.GOOD)

    setRail(streakTracker, scheduler)

    waitUntilAtLeastOneExists(hasText("1/1", substring = true))
    onNode(hasText("1/2", substring = true)).assertDoesNotExist()
  }

  // ROWS AND THE TEST-TAG CONTRACT

  @Test
  fun activeRowIsSelected() = runComposeUiTest {
    val store = InMemoryDailyActivityStore()
    setRail(StreakTracker(store), schedulerOver(dbWithDueCards(0)))

    onNodeWithTag(rowTag(NavigationBarItemContent.Training)).assertIsSelected()
    items
      .filter { it != NavigationBarItemContent.Training }
      .forEach { onNodeWithTag(rowTag(it)).assertIsNotSelected() }
  }

  @Test
  fun tappingActiveRowIsANoOp() = runComposeUiTest {
    val selected = mutableListOf<NavigationBarItemContent>()
    setRail(
      StreakTracker(InMemoryDailyActivityStore()),
      schedulerOver(dbWithDueCards(0)),
      selected = selected,
    )

    onNodeWithTag(rowTag(NavigationBarItemContent.Training)).performClick()

    selected.shouldBeEmpty()
  }

  @Test
  fun tappingInactiveRowFiresOnSelect() = runComposeUiTest {
    val selected = mutableListOf<NavigationBarItemContent>()
    setRail(
      StreakTracker(InMemoryDailyActivityStore()),
      schedulerOver(dbWithDueCards(0)),
      selected = selected,
    )

    onNodeWithTag(rowTag(NavigationBarItemContent.Settings)).performClick()

    selected shouldBe listOf(NavigationBarItemContent.Settings)
  }

  @Test
  fun railKeepsTheItemModifier() = runComposeUiTest {
    setRail(StreakTracker(InMemoryDailyActivityStore()), schedulerOver(dbWithDueCards(0)))

    items.forEach {
      onNodeWithTag(rowTag(it)).assertIsDisplayed()
      onNodeWithTag(rowTag(it)).assertHasClickAction()
    }
  }

  // BADGE — the `takeIf { it > 0 }` branch

  @Test
  fun noBadgeWhenNothingIsDue() = runComposeUiTest {
    val database = dbWithDueCards(0)
    schedulerOver(database).pendingCount() shouldBe 0

    setRail(StreakTracker(InMemoryDailyActivityStore()), schedulerOver(database))

    waitUntilAtLeastOneExists(hasText("0"))
    rowDigitTexts(rowTag(NavigationBarItemContent.Training)).shouldBeEmpty()
  }

  @Test
  fun badgeAtOne() = runComposeUiTest {
    val database = dbWithDueCards(1)
    schedulerOver(database).pendingCount() shouldBe 1

    setRail(StreakTracker(InMemoryDailyActivityStore()), schedulerOver(database))

    waitUntilAtLeastOneExists(
      hasTestTag(rowTag(NavigationBarItemContent.Training)) and hasText("1")
    )
    rowDigitTexts(rowTag(NavigationBarItemContent.Training)) shouldBe listOf("1")
  }

  @Test
  fun badgeAtSeveral() = runComposeUiTest {
    val database = dbWithDueCards(3)
    schedulerOver(database).pendingCount() shouldBe 3

    setRail(StreakTracker(InMemoryDailyActivityStore()), schedulerOver(database))

    waitUntilAtLeastOneExists(
      hasTestTag(rowTag(NavigationBarItemContent.Training)) and hasText("3")
    )
    rowDigitTexts(rowTag(NavigationBarItemContent.Training)) shouldBe listOf("3")
    rowDigitTexts(rowTag(NavigationBarItemContent.Settings)).shouldBeEmpty()
  }
}
