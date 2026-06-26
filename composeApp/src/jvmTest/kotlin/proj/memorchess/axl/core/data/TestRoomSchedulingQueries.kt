package proj.memorchess.axl.core.data

import androidx.room.Room
import io.kotest.matchers.shouldBe
import java.io.File
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import proj.memorchess.axl.core.graph.PreviousAndNextMoves
import proj.memorchess.axl.core.scheduling.CardPhase
import proj.memorchess.axl.core.scheduling.CardState

/**
 * Room backed coverage of the bounded scheduling query surface. Mirrors the predicate, ordering and
 * boundary cases of the in-memory reference against a real isolated SQLite database, proving the
 * `WHERE` / `ORDER BY` / `LIMIT` / `COUNT` clauses and the projection to
 * [proj.memorchess.axl.core.graph.TrainingEntry] match the reference semantics.
 */
class TestRoomSchedulingQueries {

  private val database: CustomDatabase = freshDatabase()
  private val manager: DatabaseQueryManager = NonJsLocalDatabaseQueryManager(database)

  private val now = Instant.fromEpochSeconds(1_000)
  private val dayStart = Instant.fromEpochSeconds(0)
  private val dayEnd = Instant.fromEpochSeconds(10_000)

  private fun freshDatabase(): CustomDatabase {
    val dbFile = File(System.getProperty("java.io.tmpdir"), "room_sched_${UUID.randomUUID()}.db")
    return getRoomDatabase(Room.databaseBuilder<CustomDatabase>(name = dbFile.absolutePath))
  }

  @AfterTest
  fun tearDown() {
    database.close()
  }

  private suspend fun insert(
    keyName: String,
    phase: CardPhase,
    dueDate: Instant,
    hasGoodOutgoing: Boolean = true,
    depth: Int = 0,
    createdAt: Instant = Instant.fromEpochSeconds(0),
    lastReview: Instant? = null,
    firstReview: Instant? = null,
    isDeleted: Boolean = false,
  ) {
    manager.insertNodes(
      DataNode(
        positionKey = PositionKey(keyName),
        previousAndNextMoves = PreviousAndNextMoves(),
        cardState =
          CardState(
            dueDate = dueDate,
            lastReview = lastReview,
            firstReview = firstReview,
            stability = 0.0,
            difficulty = 0.0,
            reps = 0,
            lapses = 0,
            phase = phase,
            step = 0,
          ),
        depth = depth,
        isDeleted = isDeleted,
        hasGoodOutgoing = hasGoodOutgoing,
        createdAt = createdAt,
      )
    )
  }

  @Test
  fun nextDueReviewCard_picksSmallestDepthAmongDueGoodReviews() = runTest {
    insert("deep", CardPhase.REVIEW, dueDate = Instant.fromEpochSeconds(100), depth = 5)
    insert("shallow", CardPhase.REVIEW, dueDate = Instant.fromEpochSeconds(200), depth = 1)
    insert(
      "notgood",
      CardPhase.REVIEW,
      dueDate = Instant.fromEpochSeconds(50),
      depth = 0,
      hasGoodOutgoing = false,
    )
    insert("new", CardPhase.NEW, dueDate = Instant.fromEpochSeconds(50), depth = 0)
    manager.nextDueReviewCard(dayEnd)?.positionKey shouldBe PositionKey("shallow")
  }

  @Test
  fun nextDueReviewCard_excludesDueDateEqualToDayEnd() = runTest {
    insert("boundary", CardPhase.REVIEW, dueDate = dayEnd, depth = 0)
    manager.nextDueReviewCard(dayEnd) shouldBe null
  }

  @Test
  fun nextDueNewCard_ordersByDepthThenCreatedAt() = runTest {
    insert(
      "d1late",
      CardPhase.NEW,
      dueDate = Instant.fromEpochSeconds(100),
      depth = 1,
      createdAt = Instant.fromEpochSeconds(500),
    )
    insert(
      "d1early",
      CardPhase.NEW,
      dueDate = Instant.fromEpochSeconds(100),
      depth = 1,
      createdAt = Instant.fromEpochSeconds(300),
    )
    insert(
      "d0",
      CardPhase.NEW,
      dueDate = Instant.fromEpochSeconds(100),
      depth = 0,
      createdAt = Instant.fromEpochSeconds(900),
    )
    manager.nextDueNewCard(dayEnd)?.positionKey shouldBe PositionKey("d0")
    manager.deletePosition(PositionKey("d0"), proj.memorchess.axl.core.graph.DeleteMode.HARD)
    manager.nextDueNewCard(dayEnd)?.positionKey shouldBe PositionKey("d1early")
  }

  @Test
  fun nextReadyLearningCard_includesDueEqualToNow() = runTest {
    insert("atNow", CardPhase.LEARNING, dueDate = now)
    manager.nextReadyLearningCard(now)?.positionKey shouldBe PositionKey("atNow")
  }

  @Test
  fun nextReadyLearningCard_picksEarliestDueAndCoversRelearning() = runTest {
    insert("late", CardPhase.LEARNING, dueDate = Instant.fromEpochSeconds(900))
    insert("early", CardPhase.RELEARNING, dueDate = Instant.fromEpochSeconds(100))
    insert("future", CardPhase.LEARNING, dueDate = Instant.fromEpochSeconds(5_000))
    manager.nextReadyLearningCard(now)?.positionKey shouldBe PositionKey("early")
  }

  @Test
  fun nextPendingLearningCard_excludesDueEqualToNow() = runTest {
    insert("atNow", CardPhase.LEARNING, dueDate = now)
    insert("future", CardPhase.RELEARNING, dueDate = Instant.fromEpochSeconds(2_000))
    manager.nextPendingLearningCard(now)?.positionKey shouldBe PositionKey("future")
  }

  @Test
  fun nextPendingLearningCard_isNullWhenAllReady() = runTest {
    insert("ready", CardPhase.LEARNING, dueDate = now)
    manager.nextPendingLearningCard(now) shouldBe null
  }

  @Test
  fun getSchedulingCounts_countsAreDayBounded() = runTest {
    val inWindow = Instant.fromEpochSeconds(5_000)
    insert(
      "introduced",
      CardPhase.REVIEW,
      dueDate = Instant.fromEpochSeconds(100),
      firstReview = inWindow,
      lastReview = inWindow,
      depth = 0,
    )
    insert(
      "trainedOnly",
      CardPhase.REVIEW,
      dueDate = Instant.fromEpochSeconds(200),
      firstReview = Instant.fromEpochSeconds(-100),
      lastReview = inWindow,
      depth = 1,
    )
    insert(
      "atBoundary",
      CardPhase.REVIEW,
      dueDate = Instant.fromEpochSeconds(300),
      firstReview = dayEnd,
      lastReview = dayEnd,
      depth = 2,
    )
    insert("dueNew", CardPhase.NEW, dueDate = Instant.fromEpochSeconds(400), depth = 3)
    insert("learning", CardPhase.LEARNING, dueDate = Instant.fromEpochSeconds(500), depth = 4)
    manager.getSchedulingCounts(dayStart, dayEnd) shouldBe
      SchedulingCounts(
        introducedToday = 1,
        trainedToday = 2,
        dueReviews = 3,
        dueNew = 1,
        inSession = 1,
      )
  }

  @Test
  fun getSchedulingCounts_emptyStoreIsAllZero() = runTest {
    manager.getSchedulingCounts(dayStart, dayEnd) shouldBe SchedulingCounts(0, 0, 0, 0, 0)
  }

  @Test
  fun findEligibleAmong_returnsFirstEligibleKeyOrNull() = runTest {
    insert("other", CardPhase.LEARNING, dueDate = now)
    insert("learning", CardPhase.LEARNING, dueDate = Instant.fromEpochSeconds(9_999))
    insert("dueReview", CardPhase.REVIEW, dueDate = Instant.fromEpochSeconds(100))
    insert("notDue", CardPhase.REVIEW, dueDate = dayEnd)
    val candidates =
      listOf(PositionKey("learning"), PositionKey("dueReview"), PositionKey("notDue"))
    manager.findEligibleAmong(candidates, dayEnd)?.positionKey shouldBe PositionKey("learning")
    manager.findEligibleAmong(emptyList(), dayEnd) shouldBe null
    manager.findEligibleAmong(listOf(PositionKey("notDue")), dayEnd) shouldBe null
  }
}
