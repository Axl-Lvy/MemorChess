package proj.memorchess.axl.dev

import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import kotlin.random.Random
import kotlin.test.Test
import kotlin.time.Instant
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import org.koin.core.component.get
import proj.memorchess.axl.core.data.DailyActivityStore
import proj.memorchess.axl.core.data.DatabaseQueryManager
import proj.memorchess.axl.core.date.DateUtil
import proj.memorchess.axl.core.graph.RepertoireTagStore
import proj.memorchess.axl.core.graph.TreeStore
import proj.memorchess.axl.core.pgn.PgnImporter
import proj.memorchess.axl.core.scheduling.CardState
import proj.memorchess.axl.core.scheduling.Fsrs6SchedulingAlgorithm
import proj.memorchess.axl.core.streak.StreakTracker
import proj.memorchess.axl.test_util.TestWithKoin

class TestDevSeeder : TestWithKoin() {

  private val zone = TimeZone.currentSystemDefault()

  private val paris = TimeZone.of("Europe/Paris")

  private suspend fun seed(now: Instant = DateUtil.now(), timeZone: TimeZone = zone) {
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
            timeZone = timeZone,
            maxNewPerDay = 10,
            maxTotalPerDay = 100,
          ),
      )
    seeder.seed(now)
  }

  private suspend fun seededCardStates(): List<CardState> {
    val database = get<DatabaseQueryManager>()
    val states = mutableListOf<CardState>()
    var cursor: String? = null
    do {
      val page = database.getNodesPage(cursor, 500)
      states += page.nodes.filter { it.hasGoodOutgoing }.map { it.cardState }
      cursor = page.nextCursor
    } while (cursor != null)
    return states
  }

  private suspend fun assertEveryBucketFilled(dateTime: String) {
    val now = LocalDateTime.parse(dateTime).toInstant(paris)

    seed(now, paris)

    for ((bucket, count) in seedBuckets(seededCardStates(), now, paris)) {
      withClue("$bucket at $dateTime") { count shouldBeGreaterThan 0 }
    }
  }

  @Test fun realSeedFillsEveryBucket() = test { assertEveryBucketFilled("2026-11-25T10:00") }

  @Test fun realSeedFillsEveryBucketOverDst() = test { assertEveryBucketFilled("2026-03-29T10:00") }

  @Test
  fun realSeedFillsEveryBucketOverNewYear() = test { assertEveryBucketFilled("2027-01-01T00:30") }

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
