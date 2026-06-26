package proj.memorchess.axl.core.data

import androidx.room.Room
import io.kotest.assertions.throwables.shouldThrow
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
import proj.memorchess.axl.core.scheduling.CardStateFactory

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

  // --- getNodesPage -------------------------------------------------------------------------

  /** Drains the whole store one page of [limit] at a time, returning the visited keys in order. */
  private suspend fun pageAllKeys(limit: Int): List<PositionKey> {
    val visited = mutableListOf<PositionKey>()
    var cursor: String? = null
    while (true) {
      val page = manager.getNodesPage(cursor, limit)
      visited += page.nodes.map { it.positionKey }
      cursor = page.nextCursor ?: break
    }
    return visited
  }

  /** Inserts [count] synthetic nodes whose keys sort deterministically, returns them ascending. */
  private suspend fun seedPageable(count: Int): List<PositionKey> {
    val keys = (0 until count).map { PositionKey("page${it.toString().padStart(3, '0')}") }
    keys.forEach { insert(it.value, CardPhase.NEW, dueDate = Instant.fromEpochSeconds(0)) }
    return keys.sortedBy { it.value }
  }

  @Test
  fun getNodesPage_pagingTheWholeStoreVisitsEveryNodeExactlyOnce() = runTest {
    val expected = seedPageable(7)
    val visited = pageAllKeys(limit = 3)
    visited shouldBe expected
    visited.toSet().size shouldBe 7
  }

  @Test
  fun getNodesPage_lastPageHasNullNextCursor() = runTest {
    seedPageable(2)
    val first = manager.getNodesPage(cursor = null, limit = 2)
    first.nodes.size shouldBe 2
    first.nextCursor shouldBe first.nodes.last().positionKey.value
    val second = manager.getNodesPage(cursor = first.nextCursor, limit = 2)
    second.nodes.isEmpty() shouldBe true
    second.nextCursor shouldBe null
  }

  @Test
  fun getNodesPage_limitLargerThanStoreReturnsEverythingWithNullCursor() = runTest {
    val expected = seedPageable(3)
    val page = manager.getNodesPage(cursor = null, limit = 100)
    page.nodes.map { it.positionKey } shouldBe expected
    page.nextCursor shouldBe null
  }

  @Test
  fun getNodesPage_storeSizeExactMultipleOfLimitTerminatesOnEmptyPage() = runTest {
    val expected = seedPageable(6)
    pageAllKeys(limit = 2) shouldBe expected
  }

  @Test
  fun getNodesPage_emptyStoreReturnsEmptyPageWithNullCursor() = runTest {
    val page = manager.getNodesPage(cursor = null, limit = 5)
    page.nodes.isEmpty() shouldBe true
    page.nextCursor shouldBe null
  }

  @Test
  fun getNodesPage_carriesEdgesLikeASingleRead() = runTest {
    // A two move line start -e4-> mid -e5-> end with the incident edges persisted.
    val start = PositionKey("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq")
    val mid = PositionKey("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq")
    val end = PositionKey("rnbqkbnr/pppp1ppp/8/4p3/4P3/8/PPPP1PPP/RNBQKBNR w KQkq")
    val e4 = DataMove(start, mid, "e4", isGood = true)
    val e5 = DataMove(mid, end, "e5", isGood = true)
    manager.insertNodes(
      DataNode(start, PreviousAndNextMoves(listOf(), listOf(e4)), CardStateFactory.new()),
      DataNode(mid, PreviousAndNextMoves(listOf(e4), listOf(e5)), CardStateFactory.new()),
      DataNode(end, PreviousAndNextMoves(listOf(e5), listOf()), CardStateFactory.new()),
    )
    val byKey =
      buildList {
          var cursor: String? = null
          while (true) {
            val page = manager.getNodesPage(cursor, limit = 1)
            addAll(page.nodes)
            cursor = page.nextCursor ?: break
          }
        }
        .associateBy { it.positionKey }
    byKey.getValue(start).previousAndNextMoves.nextMoves.keys shouldBe setOf("e4")
    byKey.getValue(mid).previousAndNextMoves.previousMoves.keys shouldBe setOf("e4")
    byKey.getValue(mid).previousAndNextMoves.nextMoves.keys shouldBe setOf("e5")
  }

  @Test
  fun getNodesPage_excludesSoftDeletedRows() = runTest {
    seedPageable(3)
    manager.deletePosition(PositionKey("page001"), proj.memorchess.axl.core.graph.DeleteMode.SOFT)
    val keys = pageAllKeys(limit = 1)
    keys.size shouldBe 2
    (PositionKey("page001") in keys) shouldBe false
  }

  @Test
  fun getNodesPage_rejectsNonPositiveLimit() = runTest {
    shouldThrow<IllegalArgumentException> { manager.getNodesPage(cursor = null, limit = 0) }
    shouldThrow<IllegalArgumentException> { manager.getNodesPage(cursor = null, limit = -1) }
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
