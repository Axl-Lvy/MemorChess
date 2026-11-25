package proj.memorchess.axl.dev

import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import kotlin.random.Random
import kotlin.test.Test
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import org.koin.core.component.get
import proj.memorchess.axl.core.data.DailyActivityStore
import proj.memorchess.axl.core.data.DatabaseQueryManager
import proj.memorchess.axl.core.date.DateUtil
import proj.memorchess.axl.core.graph.RepertoireTagStore
import proj.memorchess.axl.core.graph.TreeStore
import proj.memorchess.axl.core.pgn.PgnImporter
import proj.memorchess.axl.core.scheduling.Fsrs6SchedulingAlgorithm
import proj.memorchess.axl.core.streak.StreakTracker
import proj.memorchess.axl.test_util.TestWithKoin

class TestDevSeeder : TestWithKoin() {

  private val zone = TimeZone.currentSystemDefault()

  private suspend fun seed() {
    val tagStore = get<RepertoireTagStore>()
    val treeStore = get<TreeStore>()
    val seeder =
      DevSeeder(
        tagStore = tagStore,
        importer = PgnImporter(treeStore, tagStore),
        treeStore = treeStore,
        database = get(),
        activityStore = get(),
        simulator =
          SeedSimulator(
            algorithm =
              Fsrs6SchedulingAlgorithm(enableFuzz = { false }, enableShortTerm = { true }),
            random = Random(42),
            timeZone = zone,
            maxNewPerDay = 10,
            maxTotalPerDay = 100,
          ),
      )
    seeder.seed(DateUtil.now())
  }

  @Test
  fun seedsThreeRepertoires() = test {
    seed()

    get<RepertoireTagStore>().repertoires().map { it.id } shouldContainExactlyInAnyOrder
      listOf("dev-white", "dev-black-e4", "dev-black-d4")
  }

  @Test
  fun seedHasEnoughCards() = test {
    seed()

    val database = get<DatabaseQueryManager>()
    var cards = 0
    var cursor: String? = null
    do {
      val page = database.getNodesPage(cursor, 500)
      cards += page.nodes.count { it.hasGoodOutgoing }
      cursor = page.nextCursor
    } while (cursor != null)
    cards shouldBeGreaterThanOrEqual 150
  }

  @Test
  fun seedIsTrainableToday() = test {
    seed()

    val today = DateUtil.today()
    val counts =
      get<DatabaseQueryManager>()
        .getSchedulingCounts(
          today.atStartOfDayIn(zone),
          today.plus(DatePeriod(days = 1)).atStartOfDayIn(zone),
        )
    counts.dueReviews shouldBeGreaterThan 0
    counts.dueNew shouldBeGreaterThan 0
  }

  @Test
  fun seedHasAStreak() = test {
    seed()

    StreakTracker(get<DailyActivityStore>()).streakDays() shouldBeGreaterThanOrEqual 14
  }

  @Test
  fun seedQueuesItselfForSync() = test {
    seed()

    get<DatabaseQueryManager>().getOutbox().shouldNotBeEmpty()
  }

  @Test
  fun seedHasMasteredCards() = test {
    seed()

    get<RepertoireTagStore>().masterySnapshots().values.maxOf { it.solidCount } shouldBeGreaterThan
      0
  }
}
