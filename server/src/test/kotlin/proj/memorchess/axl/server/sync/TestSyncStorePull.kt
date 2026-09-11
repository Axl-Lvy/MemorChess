package proj.memorchess.axl.server.sync

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import proj.memorchess.axl.core.sync.DevicePlatform
import proj.memorchess.axl.core.sync.NodeSyncRow
import proj.memorchess.axl.core.sync.SettingSyncRow
import proj.memorchess.axl.core.sync.SyncPushRequest
import proj.memorchess.axl.server.db.PostgresTestDb

internal class TestSyncStorePull {

  private val store = SyncStore(PostgresTestDb.dataSource())

  /** A fresh user with one registered device, which pushing now requires. */
  private suspend fun newUser(): String {
    val user = PostgresTestDb.newUserId()
    store.registerDevice(user, DEVICE, DevicePlatform.JVM, afterReset = false, serverNow)
    return user
  }

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
    store.push(
      user,
      DEVICE,
      SyncPushRequest(emptyList(), emptyList(), rows.toList(), device = DEVICE),
      serverNow,
    )

  /** A fresh user with one registered device, which pulling now requires. */
  private suspend fun registeredUser(): String {
    val user = PostgresTestDb.newUserId()
    store.registerDevice(user, DEVICE, DevicePlatform.JVM, afterReset = false, serverNow)
    return user
  }

  /** The device's next page, confirming [ack] first. */
  private suspend fun pull(user: String, limit: Int = 10, ack: String? = null) =
    store.pull(user, DEVICE, ack, limit, serverNow)

  @Test
  fun aNonPositiveLimitIsRejected() = runTest {
    shouldThrow<IllegalArgumentException> { store.pull(newUser(), DEVICE, null, 0, serverNow) }
    shouldThrow<IllegalArgumentException> { store.pull(newUser(), DEVICE, null, -1, serverNow) }
  }

  @Test
  fun emptyStoreReturnsNoRowsAndANullCursor() = runTest {
    val page = pull(registeredUser())
    page.nodes.shouldBeEmpty()
    page.edges.shouldBeEmpty()
    page.settings.shouldBeEmpty()
    page.nextCursor shouldBe null
    page.serverTime shouldBe serverNow
  }

  @Test
  fun aSingleRowComesBackAndTheCursorTerminates() = runTest {
    val user = registeredUser()
    pushSettings(user, setting("theme", "dark"))
    val page = pull(user)
    page.settings shouldHaveSize 1
    page.nextCursor shouldBe null
  }

  @Test
  fun acknowledgingAPageIsServedNothingFurther() = runTest {
    val user = registeredUser()
    pushSettings(user, setting("a", "1"), setting("b", "2"))
    val first = pull(user, limit = 2)
    first.settings shouldHaveSize 2
    val second = pull(user, limit = 2, ack = first.pageToken)
    second.settings.shouldBeEmpty()
    second.nextCursor shouldBe null
  }

  @Test
  fun aFreshDeviceIsServedEverything() = runTest {
    val user = registeredUser()
    pushSettings(user, setting("a", "1"), setting("b", "2"), setting("c", "3"))
    pull(user).settings shouldHaveSize 3
  }

  @Test
  fun aDevicePositionedAboveEveryRevisionIsServedNothing() = runTest {
    val user = registeredUser()
    pushSettings(user, setting("a", "1"))
    store.setPositionForTest(user, DEVICE, lastAcked = Long.MAX_VALUE - 1, lastServed = 0)

    pull(user).settings.shouldBeEmpty()
  }

  @Test
  fun aLimitOfOneWalksTheWholeStoreOneRowAtATime() = runTest {
    val user = registeredUser()
    pushSettings(user, setting("a", "1"), setting("b", "2"), setting("c", "3"))
    var ack: String? = null
    val seen = mutableListOf<String>()
    var pages = 0
    while (true) {
      val page = pull(user, limit = 1, ack = ack)
      if (page.settings.isEmpty()) break
      seen += page.settings.map { it.key }
      ack = page.pageToken
      if (pages++ > 10) error("paging did not terminate")
    }
    seen shouldBe listOf("a", "b", "c")
  }

  @Test
  fun aStoreSizeThatIsAnExactMultipleOfTheLimitEndsWithAnEmptyPage() = runTest {
    val user = registeredUser()
    pushSettings(user, setting("a", "1"), setting("b", "2"))
    val first = pull(user, limit = 2)
    first.settings shouldHaveSize 2
    // A full page cannot know it was the last, so one empty page follows and confirms it.
    val second = pull(user, limit = 2, ack = first.pageToken)
    second.settings.shouldBeEmpty()
    second.nextCursor shouldBe null
  }

  @Test
  fun rowsComeBackInRevisionOrder() = runTest {
    val user = registeredUser()
    pushSettings(user, setting("first", "1"))
    pushSettings(user, setting("second", "2"))
    pushSettings(user, setting("third", "3"))
    pull(user).settings.map { it.key } shouldBe listOf("first", "second", "third")
  }

  @Test
  fun tombstonesAreReturnedLikeAnyOtherRow() = runTest {
    val user = registeredUser()
    pushSettings(user, setting("theme", "dark", seq = 1))
    pushSettings(user, setting("theme", "dark", seq = 2).copy(isDeleted = true))
    pull(user).settings.single().isDeleted shouldBe true
  }

  @Test
  fun anotherUsersRowsAreNeverReturned() = runTest {
    val mine = registeredUser()
    val theirs = newUser()
    pushSettings(theirs, setting("theme", "dark"))
    pull(mine).settings.shouldBeEmpty()
  }

  @Test
  fun allThreeResourcesComeBackInOnePage() = runTest {
    val user = registeredUser()
    val key = fen("mixed")
    store.push(
      user,
      DEVICE,
      SyncPushRequest(
        listOf(node(key)),
        emptyList(),
        listOf(setting("theme", "dark")),
        device = DEVICE,
      ),
      serverNow,
    )
    val page = pull(user)
    page.nodes shouldHaveSize 1
    page.settings shouldHaveSize 1
  }

  @Test
  fun aFullPageInOneTableCapsTheCursorForTheOthers() = runTest {
    val user = registeredUser()
    // Settings take the LOW revisions, nodes the high ones.
    pushSettings(user, setting("a", "1"))
    pushSettings(user, setting("b", "2"))
    pushSettings(user, setting("c", "3"))
    store.push(
      user,
      DEVICE,
      SyncPushRequest(listOf(node(fen("n1"))), emptyList(), emptyList(), device = DEVICE),
      serverNow,
    )
    store.push(
      user,
      DEVICE,
      SyncPushRequest(listOf(node(fen("n2"))), emptyList(), emptyList(), device = DEVICE),
      serverNow,
    )

    // With limit 2 the settings page fills and its ceiling is the second setting's revision, which
    // is BELOW both node revisions. The nodes must therefore be withheld entirely, or the caller
    // would advance its cursor past settings it never received.
    val page = pull(user, limit = 2)
    page.settings.map { it.key } shouldBe listOf("a", "b")
    page.nodes.shouldBeEmpty()

    // The withheld rows arrive on later pages, and nothing is lost.
    var ack = page.pageToken
    val settingsSeen = page.settings.map { it.key }.toMutableList()
    var nodesSeen = 0
    var guard = 0
    while (true) {
      val next = pull(user, limit = 2, ack = ack)
      if (next.settings.isEmpty() && next.nodes.isEmpty()) break
      settingsSeen += next.settings.map { it.key }
      nodesSeen += next.nodes.size
      ack = next.pageToken
      if (guard++ > 10) error("paging did not terminate")
    }
    settingsSeen shouldBe listOf("a", "b", "c")
    nodesSeen shouldBe 2
  }

  private companion object {
    const val DEVICE = "33333333-3333-4333-8333-333333333333"
  }
}
