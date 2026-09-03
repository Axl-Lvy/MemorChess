package proj.memorchess.axl.server.sync

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import proj.memorchess.axl.core.sync.NodeSyncRow
import proj.memorchess.axl.core.sync.SettingSyncRow
import proj.memorchess.axl.core.sync.SyncPushRequest
import proj.memorchess.axl.server.db.PostgresTestDb

internal class TestSyncStorePull {

  private val store = SyncStore(PostgresTestDb.dataSource())
  private val serverNow = Instant.fromEpochMilliseconds(1_000_000)

  private fun setting(key: String, value: String, seq: Long = 1) =
    SettingSyncRow(
      key = key,
      value = value,
      isDeleted = false,
      updatedAt = serverNow,
      originDevice = "device-a",
      deviceSeq = seq,
    )

  private fun node(key: String) =
    NodeSyncRow(
      positionKey = key,
      dueDate = serverNow,
      lastReview = null,
      firstReview = null,
      stability = 1.0,
      difficulty = 1.0,
      reps = 0,
      lapses = 0,
      phase = "NEW",
      step = 0,
      isDeleted = false,
      updatedAt = serverNow,
      originDevice = "device-a",
      deviceSeq = 1,
    )

  private fun fen(suffix: String) = "fen-${System.nanoTime()}-$suffix"

  private suspend fun pushSettings(user: String, vararg rows: SettingSyncRow) =
    store.push(user, SyncPushRequest(emptyList(), emptyList(), rows.toList()), serverNow)

  @Test
  fun aNonPositiveLimitIsRejected() = runTest {
    shouldThrow<IllegalArgumentException> {
      store.pull(PostgresTestDb.newUserId(), 0, 0, serverNow)
    }
    shouldThrow<IllegalArgumentException> {
      store.pull(PostgresTestDb.newUserId(), 0, -1, serverNow)
    }
  }

  @Test
  fun emptyStoreReturnsNoRowsAndANullCursor() = runTest {
    val page = store.pull(PostgresTestDb.newUserId(), 0, 10, serverNow)
    page.nodes.shouldBeEmpty()
    page.edges.shouldBeEmpty()
    page.settings.shouldBeEmpty()
    page.nextCursor shouldBe null
    page.serverTime shouldBe serverNow
  }

  @Test
  fun aSingleRowComesBackAndTheCursorTerminates() = runTest {
    val user = PostgresTestDb.newUserId()
    pushSettings(user, setting("theme", "dark"))
    val page = store.pull(user, 0, 10, serverNow)
    page.settings shouldHaveSize 1
    page.nextCursor shouldBe null
  }

  @Test
  fun pullingFromTheReturnedCursorReturnsNothingFurther() = runTest {
    val user = PostgresTestDb.newUserId()
    pushSettings(user, setting("a", "1"), setting("b", "2"))
    val first = store.pull(user, 0, 2, serverNow)
    first.settings shouldHaveSize 2
    val second = store.pull(user, first.nextCursor!!, 2, serverNow)
    second.settings.shouldBeEmpty()
    second.nextCursor shouldBe null
  }

  @Test
  fun aZeroCursorReturnsEverything() = runTest {
    val user = PostgresTestDb.newUserId()
    pushSettings(user, setting("a", "1"), setting("b", "2"), setting("c", "3"))
    store.pull(user, 0, 10, serverNow).settings shouldHaveSize 3
  }

  @Test
  fun aCursorAboveEveryRevisionReturnsNothing() = runTest {
    val user = PostgresTestDb.newUserId()
    pushSettings(user, setting("a", "1"))
    store.pull(user, Long.MAX_VALUE - 1, 10, serverNow).settings.shouldBeEmpty()
  }

  @Test
  fun aLimitOfOneWalksTheWholeStoreOneRowAtATime() = runTest {
    val user = PostgresTestDb.newUserId()
    pushSettings(user, setting("a", "1"), setting("b", "2"), setting("c", "3"))
    var cursor = 0L
    val seen = mutableListOf<String>()
    var pages = 0
    while (true) {
      val page = store.pull(user, cursor, 1, serverNow)
      seen += page.settings.map { it.key }
      pages++
      cursor = page.nextCursor ?: break
      if (pages > 10) error("paging did not terminate")
    }
    seen shouldBe listOf("a", "b", "c")
  }

  @Test
  fun aStoreSizeThatIsAnExactMultipleOfTheLimitEndsWithAnEmptyPage() = runTest {
    val user = PostgresTestDb.newUserId()
    pushSettings(user, setting("a", "1"), setting("b", "2"))
    val first = store.pull(user, 0, 2, serverNow)
    first.settings shouldHaveSize 2
    // A full page cannot know it was the last, so it returns a cursor and one empty page follows.
    val second = store.pull(user, first.nextCursor!!, 2, serverNow)
    second.settings.shouldBeEmpty()
    second.nextCursor shouldBe null
  }

  @Test
  fun rowsComeBackInRevisionOrder() = runTest {
    val user = PostgresTestDb.newUserId()
    pushSettings(user, setting("first", "1"))
    pushSettings(user, setting("second", "2"))
    pushSettings(user, setting("third", "3"))
    store.pull(user, 0, 10, serverNow).settings.map { it.key } shouldBe
      listOf("first", "second", "third")
  }

  @Test
  fun tombstonesAreReturnedLikeAnyOtherRow() = runTest {
    val user = PostgresTestDb.newUserId()
    pushSettings(user, setting("theme", "dark", seq = 1))
    pushSettings(user, setting("theme", "dark", seq = 2).copy(isDeleted = true))
    val page = store.pull(user, 0, 10, serverNow)
    page.settings.single().isDeleted shouldBe true
  }

  @Test
  fun anotherUsersRowsAreNeverReturned() = runTest {
    val mine = PostgresTestDb.newUserId()
    val theirs = PostgresTestDb.newUserId()
    pushSettings(theirs, setting("theme", "dark"))
    store.pull(mine, 0, 10, serverNow).settings.shouldBeEmpty()
  }

  @Test
  fun allThreeResourcesComeBackInOnePage() = runTest {
    val user = PostgresTestDb.newUserId()
    val key = fen("mixed")
    store.push(
      user,
      SyncPushRequest(listOf(node(key)), emptyList(), listOf(setting("theme", "dark"))),
      serverNow,
    )
    val page = store.pull(user, 0, 10, serverNow)
    page.nodes shouldHaveSize 1
    page.settings shouldHaveSize 1
  }

  @Test
  fun aFullPageInOneTableCapsTheCursorForTheOthers() = runTest {
    val user = PostgresTestDb.newUserId()
    // Settings take the LOW revisions, nodes the high ones.
    pushSettings(user, setting("a", "1"))
    pushSettings(user, setting("b", "2"))
    pushSettings(user, setting("c", "3"))
    store.push(user, SyncPushRequest(listOf(node(fen("n1"))), emptyList(), emptyList()), serverNow)
    store.push(user, SyncPushRequest(listOf(node(fen("n2"))), emptyList(), emptyList()), serverNow)

    // With limit 2 the settings page fills and its ceiling is the second setting's revision, which
    // is BELOW both node revisions. The nodes must therefore be withheld entirely, or the caller
    // would advance its cursor past settings it never received.
    val page = store.pull(user, 0, 2, serverNow)
    page.settings.map { it.key } shouldBe listOf("a", "b")
    page.nodes.shouldBeEmpty()

    // The withheld rows arrive on later pages, and nothing is lost.
    var cursor = page.nextCursor!!
    val settingsSeen = page.settings.map { it.key }.toMutableList()
    var nodesSeen = 0
    var guard = 0
    while (true) {
      val next = store.pull(user, cursor, 2, serverNow)
      settingsSeen += next.settings.map { it.key }
      nodesSeen += next.nodes.size
      cursor = next.nextCursor ?: break
      if (guard++ > 10) error("paging did not terminate")
    }
    settingsSeen shouldBe listOf("a", "b", "c")
    nodesSeen shouldBe 2
  }
}
