package proj.memorchess.axl.server.repertoire

import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlin.test.Test
import kotlin.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import proj.memorchess.axl.core.data.repertoire.RepertoirePublishLimits
import proj.memorchess.axl.server.db.PostgresTestDb

internal class TestRepertoireStore {

  private val now = Instant.fromEpochSeconds(1_700_000_000)

  // A random id, unique per call and unrelated (in sort order) to any other test's ids: the
  // repertoire table is global with no per test user scoping, since the catalog is anonymous by
  // design, so this is what keeps one test's rows from leaking into another's assertions.
  private fun newId(): String = java.util.UUID.randomUUID().toString()

  // Fresh per test method (a new instance is created for every @Test), so the per author quota
  // counted against the shared table never accumulates across tests the way a literal
  // "author-1" would.
  private fun newAuthor(): String = "author-${java.util.UUID.randomUUID()}"

  private val author1 = newAuthor()
  private val author2 = newAuthor()

  private fun store(
    maxMoves: Int = RepertoirePublishLimits.MAX_REPERTOIRE_MOVES,
    maxRepertoiresPerUser: Int = MAX_REPERTOIRES_PER_USER,
    maxTotalMovesPerUser: Int = MAX_TOTAL_MOVES_PER_USER,
  ) =
    RepertoireStore(
      dataSource = PostgresTestDb.dataSource(),
      maxMoves = maxMoves,
      maxRepertoiresPerUser = maxRepertoiresPerUser,
      maxTotalMovesPerUser = maxTotalMovesPerUser,
    )

  private fun moves(vararg sans: String = arrayOf("e4")) = line(*sans)

  /** How many `move_edge` rows play [move] from [from]. */
  private fun edgeRows(from: String, move: String): Int =
    PostgresTestDb.dataSource().connection.use { connection ->
      connection
        .prepareStatement(
          "SELECT count(*) FROM move_edge e JOIN position p ON p.id = e.origin_id " +
            "WHERE p.position_key = ? AND e.move = ?"
        )
        .use { statement ->
          statement.setString(1, from)
          statement.setString(2, move)
          statement.executeQuery().use { rows ->
            rows.next()
            rows.getInt(1)
          }
        }
    }

  @Test
  fun `publishing a new repertoire stores it at version 1 with every move and marking`() =
    kotlinx.coroutines.test.runTest {
      val store = store()
      val id = newId()
      val published = moves("e4", "e5", "Nf3")

      val outcome =
        store.publish(
          authorId = author1,
          id = id,
          title = "Title",
          description = "Desc",
          side = "white",
          moves = published,
          now = now,
        )

      outcome.shouldBeInstanceOf<PublishOutcome.Published>()
      outcome.row.version shouldBe 1
      outcome.row.status shouldBe "published"
      outcome.row.moveCount shouldBe 3
      store.moves(id, 1).shouldNotBeNull().shouldContainExactlyInAnyOrder(published)
    }

  @Test
  fun `publishing again for the same author creates version 2 and keeps version 1 untouched`() =
    kotlinx.coroutines.test.runTest {
      val store = store()
      val id = newId()
      store.publish(author1, id, "T", "D", "white", moves("e4"), now)

      val second = store.publish(author1, id, "T2", "D2", "black", moves("d4"), now)

      second.shouldBeInstanceOf<PublishOutcome.Published>()
      second.row.version shouldBe 2
      second.row.title shouldBe "T2"
      store.moves(id, 1) shouldBe moves("e4")
      store.moves(id, 2) shouldBe moves("d4")
    }

  @Test
  fun `publishing for an id owned by a different author is forbidden`() =
    kotlinx.coroutines.test.runTest {
      val store = store()
      val id = newId()
      store.publish(author1, id, "T", "D", "white", moves(), now)

      val outcome = store.publish(author2, id, "T", "D", "white", moves("d4"), now)

      outcome shouldBe PublishOutcome.Forbidden
    }

  @Test
  fun `publishing rejects an empty move list`() =
    kotlinx.coroutines.test.runTest {
      val outcome = store().publish(author1, newId(), "T", "D", "white", emptyList(), now)

      outcome.shouldBeInstanceOf<PublishOutcome.InvalidPayload>()
    }

  @Test
  fun `publishing an illegal move is refused and writes no version`() =
    kotlinx.coroutines.test.runTest {
      val store = store()
      val id = newId()
      val illegal = moves("e4").map { it.copy(move = "e5") }

      val outcome = store.publish(author1, id, "T", "D", "white", illegal, now)

      outcome.shouldBeInstanceOf<PublishOutcome.InvalidPayload>()
      store.get(id).shouldBeNull()
      store.moves(id, 1).shouldBeNull()
    }

  @Test
  fun `publishing rejects one move over the move cap`() =
    kotlinx.coroutines.test.runTest {
      val outcome =
        store(maxMoves = 1).publish(author1, newId(), "T", "D", "white", moves("e4", "e5"), now)

      outcome.shouldBeInstanceOf<PublishOutcome.PayloadTooLarge>()
    }

  @Test
  fun `publishing accepts exactly the move cap`() =
    kotlinx.coroutines.test.runTest {
      val outcome =
        store(maxMoves = 2).publish(author1, newId(), "T", "D", "white", moves("e4", "e5"), now)

      outcome.shouldBeInstanceOf<PublishOutcome.Published>()
    }

  @Test
  fun `publishing rejects an id shape that would break the client install store`() =
    kotlinx.coroutines.test.runTest {
      val outcome = store().publish(author1, "bad,id", "T", "D", "white", moves(), now)

      outcome.shouldBeInstanceOf<PublishOutcome.InvalidPayload>()
    }

  @Test
  fun `publishing rejects an id that is too short`() =
    kotlinx.coroutines.test.runTest {
      val outcome = store().publish(author1, "ab", "T", "D", "white", moves(), now)

      outcome.shouldBeInstanceOf<PublishOutcome.InvalidPayload>()
    }

  @Test
  fun `publishing accepts a title of exactly the length cap`() =
    kotlinx.coroutines.test.runTest {
      val title = "t".repeat(200)

      val outcome = store().publish(author1, newId(), title, "D", "white", moves(), now)

      outcome.shouldBeInstanceOf<PublishOutcome.Published>()
    }

  @Test
  fun `publishing rejects a title one character over the length cap`() =
    kotlinx.coroutines.test.runTest {
      val title = "t".repeat(201)

      val outcome = store().publish(author1, newId(), title, "D", "white", moves(), now)

      outcome.shouldBeInstanceOf<PublishOutcome.InvalidPayload>()
    }

  @Test
  fun `publishing accepts a description of exactly the length cap`() =
    kotlinx.coroutines.test.runTest {
      val description = "d".repeat(2_000)

      val outcome = store().publish(author1, newId(), "T", description, "white", moves(), now)

      outcome.shouldBeInstanceOf<PublishOutcome.Published>()
    }

  @Test
  fun `publishing rejects a description one character over the length cap`() =
    kotlinx.coroutines.test.runTest {
      val description = "d".repeat(2_001)

      val outcome = store().publish(author1, newId(), "T", description, "white", moves(), now)

      outcome.shouldBeInstanceOf<PublishOutcome.InvalidPayload>()
    }

  @Test
  fun `publishing refused by the quota writes no version`() =
    kotlinx.coroutines.test.runTest {
      val store = store(maxRepertoiresPerUser = 1)
      val author = "author-orphan-${System.nanoTime()}"
      store.publish(author, newId(), "T", "D", "white", moves("e4"), now)
      val rejectedId = newId()

      val outcome = store.publish(author, rejectedId, "T", "D", "white", moves("d4"), now)

      outcome.shouldBeInstanceOf<PublishOutcome.QuotaExceeded>()
      store.moves(rejectedId, 1).shouldBeNull()
    }

  @Test
  fun `publishing the one over the repertoire count quota is refused`() =
    kotlinx.coroutines.test.runTest {
      val store = store(maxRepertoiresPerUser = 2)
      val author = "author-quota-${System.nanoTime()}"
      store.publish(author, newId(), "T", "D", "white", moves("e4"), now)
      store.publish(author, newId(), "T", "D", "white", moves("d4"), now)

      val outcome = store.publish(author, newId(), "T", "D", "white", moves("c4"), now)

      outcome.shouldBeInstanceOf<PublishOutcome.QuotaExceeded>()
    }

  @Test
  fun `publishing accepts exactly maxRepertoiresPerUser repertoires`() =
    kotlinx.coroutines.test.runTest {
      val store = store(maxRepertoiresPerUser = 2)
      val author = "author-quota-ok-${System.nanoTime()}"
      store.publish(author, newId(), "T", "D", "white", moves("e4"), now)

      val outcome = store.publish(author, newId(), "T", "D", "white", moves("d4"), now)

      outcome.shouldBeInstanceOf<PublishOutcome.Published>()
    }

  @Test
  fun `the move quota counts the author's other non removed repertoires`() =
    kotlinx.coroutines.test.runTest {
      val store = store(maxTotalMovesPerUser = 4)
      val author = "author-moves-${System.nanoTime()}"
      store.publish(author, newId(), "T", "D", "white", moves("e4", "e5", "Nf3"), now)

      val fits = store.publish(author, newId(), "T", "D", "white", moves("d4"), now)
      val over = store.publish(author, newId(), "T", "D", "white", moves("c4"), now)

      fits.shouldBeInstanceOf<PublishOutcome.Published>()
      over.shouldBeInstanceOf<PublishOutcome.QuotaExceeded>()
    }

  @Test
  fun `republishing the same id does not count its own prior version against the move quota`() =
    kotlinx.coroutines.test.runTest {
      val store = store(maxTotalMovesPerUser = 2)
      val author = "author-republish-${System.nanoTime()}"
      val id = newId()
      store.publish(author, id, "T", "D", "white", moves("e4", "e5"), now)

      val outcome = store.publish(author, id, "T2", "D2", "white", moves("d4", "d5"), now)

      outcome.shouldBeInstanceOf<PublishOutcome.Published>()
      outcome.row.version shouldBe 2
    }

  @Test
  fun `a removed repertoire's moves no longer count against the move quota`() =
    kotlinx.coroutines.test.runTest {
      val store = store(maxTotalMovesPerUser = 2)
      val author = "author-removed-moves-${System.nanoTime()}"
      val removed = newId()
      store.publish(author, removed, "T", "D", "white", moves("e4", "e5"), now)
      store.remove(author, removed)

      val outcome = store.publish(author, newId(), "T", "D", "white", moves("d4", "d5"), now)

      outcome.shouldBeInstanceOf<PublishOutcome.Published>()
    }

  @Test
  fun `remove sets status to removed and get returns null afterward`() =
    kotlinx.coroutines.test.runTest {
      val store = store()
      val id = newId()
      store.publish(author1, id, "T", "D", "white", moves(), now)

      val outcome = store.remove(author1, id)

      outcome shouldBe RemoveOutcome.Removed
      store.get(id).shouldBeNull()
    }

  @Test
  fun `remove by a non author is forbidden and leaves the row published`() =
    kotlinx.coroutines.test.runTest {
      val store = store()
      val id = newId()
      store.publish(author1, id, "T", "D", "white", moves(), now)

      val outcome = store.remove(author2, id)

      outcome shouldBe RemoveOutcome.Forbidden
      store.get(id).shouldNotBeNull()
    }

  @Test
  fun `remove of an unknown id returns NotFound`() =
    kotlinx.coroutines.test.runTest {
      val outcome = store().remove(author1, newId())

      outcome shouldBe RemoveOutcome.NotFound
    }

  @Test
  fun `a move shared by two repertoires is interned once`() =
    kotlinx.coroutines.test.runTest {
      val store = store()
      val e4 = moves("e4").single()
      store.publish(author1, newId(), "T", "D", "white", moves("e4", "e5"), now)

      store.publish(author2, newId(), "T", "D", "white", moves("e4", "c5"), now)

      edgeRows(e4.from, e4.move) shouldBe 1
    }

  @Test
  fun `setStatus moves a repertoire to unlisted and get still returns it`() =
    kotlinx.coroutines.test.runTest {
      val store = store()
      val id = newId()
      store.publish(author1, id, "T", "D", "white", moves(), now)

      val outcome = store.setStatus(id, "unlisted")

      outcome.shouldBeInstanceOf<SetStatusOutcome.Updated>()
      outcome.row.status shouldBe "unlisted"
      store.get(id)?.status shouldBe "unlisted"
    }

  @Test
  fun `setStatus to removed makes every version's moves unavailable`() =
    kotlinx.coroutines.test.runTest {
      val store = store()
      val id = newId()
      store.publish(author1, id, "T", "D", "white", moves("e4"), now)
      store.publish(author1, id, "T", "D", "white", moves("d4"), now)

      store.setStatus(id, "removed")

      store.moves(id, 1).shouldBeNull()
      store.moves(id, 2).shouldBeNull()
      store.get(id).shouldBeNull()
    }

  @Test
  fun `setStatus on an unknown id returns NotFound`() =
    kotlinx.coroutines.test.runTest {
      val outcome = store().setStatus(newId(), "unlisted")

      outcome shouldBe SetStatusOutcome.NotFound
    }

  @Test
  fun `setStatus refuses to resurrect a removed repertoire`() =
    kotlinx.coroutines.test.runTest {
      val store = store()
      val id = newId()
      store.publish(author1, id, "T", "D", "white", moves(), now)
      store.remove(author1, id)

      val outcome = store.setStatus(id, "published")

      outcome shouldBe SetStatusOutcome.NotFound
      store.get(id).shouldBeNull()
    }

  @Test
  fun `get returns null for a status of removed`() =
    kotlinx.coroutines.test.runTest {
      val store = store()
      val id = newId()
      store.publish(author1, id, "T", "D", "white", moves(), now)
      store.remove(author1, id)

      store.get(id).shouldBeNull()
    }

  @Test
  fun `listPublished only returns published repertoires, not unlisted or removed`() =
    kotlinx.coroutines.test.runTest {
      val store = store()
      val author = "author-list-${System.nanoTime()}"
      val published = newId()
      val unlisted = newId()
      val removed = newId()
      store.publish(author, published, "T", "D", "white", moves("e4"), now)
      store.publish(author, unlisted, "T", "D", "white", moves("d4"), now)
      store.setStatus(unlisted, "unlisted")
      store.publish(author, removed, "T", "D", "white", moves("c4"), now)
      store.remove(author, removed)

      // The table is global (no per test scoping, since the catalog is anonymous by design), so
      // this checks containment rather than exact equality against the whole table's contents.
      val allIds = store.allPublished().map { it.id }

      allIds shouldContain published
      (unlisted in allIds) shouldBe false
      (removed in allIds) shouldBe false
    }

  @Test
  fun `listPublished pages with a cursor and nextCursor is present only when the page is full`() =
    kotlinx.coroutines.test.runTest {
      val store = store()
      val author = "author-page-${System.nanoTime()}"
      // Same random prefix for all three ids, and every other test's id is an unrelated random
      // UUID, so listing from cursor = prefix (which sorts just before "$prefix-1") lands exactly
      // on these three ids with nothing else interleaved, regardless of what earlier tests wrote.
      val prefix = java.util.UUID.randomUUID().toString()
      val ids = listOf("$prefix-1", "$prefix-2", "$prefix-3")
      ids.forEach { store.publish(author, it, "T", "D", "white", moves("e4"), now) }

      val firstPage = store.listPublished(cursor = prefix, limit = 2)
      firstPage.rows.map { it.id } shouldBe ids.take(2)
      firstPage.nextCursor shouldBe ids[1]

      // A huge limit here (rather than 2) is deliberate: with only one of "my" ids left after the
      // cursor, a limit of 2 would legitimately pick up whatever unrelated repertoire another test
      // happened to publish next in the global, unscoped id order. A limit far beyond this whole
      // suite's total row count instead guarantees the page comes back short, so nextCursor is null
      // for a real reason (not full) rather than by luck.
      val secondPage = store.listPublished(cursor = firstPage.nextCursor, limit = 100_000)
      secondPage.rows.first().id shouldBe ids[2]
      secondPage.nextCursor.shouldBeNull()
    }

  @Test
  fun `listPublished returns each id once even when it has multiple versions`() =
    kotlinx.coroutines.test.runTest {
      val store = store()
      val author = "author-versions-${System.nanoTime()}"
      val id = newId()
      store.publish(author, id, "T1", "D", "white", moves("e4"), now)
      store.publish(author, id, "T2", "D", "white", moves("d4"), now)

      val page = store.listPublished(cursor = null, limit = 100)

      page.rows.count { it.id == id } shouldBe 1
      page.rows.first { it.id == id }.title shouldBe "T2"
    }

  @Test
  fun `allPublished returns every published repertoire unpaginated`() =
    kotlinx.coroutines.test.runTest {
      val store = store()
      val author = "author-all-${System.nanoTime()}"
      val ids = (1..5).map { newId() }
      ids.forEach { store.publish(author, it, "T", "D", "white", moves("e4"), now) }

      val all = store.allPublished()

      ids.forEach { id -> all.map { it.id } shouldContain id }
    }

  @Test
  fun `moves of an unlisted version are still served`() =
    kotlinx.coroutines.test.runTest {
      val store = store()
      val id = newId()
      store.publish(author1, id, "T", "D", "white", moves("e4"), now)

      store.setStatus(id, "unlisted")

      store.moves(id, 1) shouldBe moves("e4")
    }

  @Test
  fun `moves of an unknown version or id are null`() =
    kotlinx.coroutines.test.runTest {
      val store = store()
      val id = newId()
      store.publish(author1, id, "T", "D", "white", moves("e4"), now)

      store.moves(id, 2).shouldBeNull()
      store.moves(id, 0).shouldBeNull()
      store.moves(newId(), 1).shouldBeNull()
    }

  @Test
  fun `publish is refused for an id whose latest version a moderator removed`() =
    kotlinx.coroutines.test.runTest {
      val store = store()
      val id = newId()
      store.publish(author1, id, "T", "D", "white", moves(), now)
      store.setStatus(id, "removed")

      val outcome = store.publish(author1, id, "T2", "D2", "white", moves("d4"), now)

      outcome shouldBe PublishOutcome.Removed
    }

  @Test
  fun `republishing a removed id still counts against the repertoire count quota`() =
    kotlinx.coroutines.test.runTest {
      val store = store(maxRepertoiresPerUser = 1)
      val author = "author-quota-removed-${System.nanoTime()}"
      val removedId = newId()
      store.publish(author, removedId, "T", "D", "white", moves("e4"), now)
      store.remove(author, removedId)
      val otherId = newId()
      store.publish(author, otherId, "T", "D", "white", moves("d4"), now)

      val outcome = store.publish(author, removedId, "T2", "D2", "white", moves("c4"), now)

      outcome.shouldBeInstanceOf<PublishOutcome.QuotaExceeded>()
    }

  @Test
  fun `remove makes every superseded version's moves unavailable`() =
    kotlinx.coroutines.test.runTest {
      val store = store()
      val id = newId()
      store.publish(author1, id, "T", "D", "white", moves("e4"), now)
      store.publish(author1, id, "T2", "D2", "white", moves("d4"), now)

      store.remove(author1, id)

      store.moves(id, 1).shouldBeNull()
      store.moves(id, 2).shouldBeNull()
    }

  @Test
  fun `concurrent publishes for the same id never collide and land on distinct versions`() =
    kotlinx.coroutines.test.runTest {
      val store = store()
      val id = newId()
      val concurrency = 8

      val outcomes = coroutineScope {
        (1..concurrency)
          .map { n ->
            async(Dispatchers.IO) { store.publish(author1, id, "T$n", "D", "white", moves(), now) }
          }
          .map { it.await() }
      }

      outcomes.forEach { it.shouldBeInstanceOf<PublishOutcome.Published>() }
      val versions = outcomes.map { (it as PublishOutcome.Published).row.version }
      versions.toSet() shouldBe (1..concurrency).toSet()
    }

  @Test
  fun `an id with no recorded installs has a zero count`() =
    kotlinx.coroutines.test.runTest {
      val store = store()
      val id = newId()

      store.countsFor(listOf(id)) shouldBe mapOf(id to 0L)
    }

  @Test
  fun `recordInstall increments the count for that id only`() =
    kotlinx.coroutines.test.runTest {
      val store = store()
      val id = newId()
      val otherId = newId()

      store.recordInstall(id)
      store.recordInstall(id)

      store.countsFor(listOf(id, otherId)) shouldBe mapOf(id to 2L, otherId to 0L)
    }

  @Test
  fun `recordInstall survives a republish under a new version`() =
    kotlinx.coroutines.test.runTest {
      val store = store()
      val id = newId()
      store.publish(author1, id, "T", "D", "white", moves(), now)
      store.recordInstall(id)

      store.publish(author1, id, "T2", "D2", "white", moves("d4"), now)

      store.countsFor(listOf(id)) shouldBe mapOf(id to 1L)
    }

  @Test
  fun `countsFor an empty id list returns an empty map`() =
    kotlinx.coroutines.test.runTest { store().countsFor(emptyList()) shouldBe emptyMap() }

  @Test
  fun `recordInstall on an unpublished id still counts it, harmlessly ignored by the catalog`() =
    kotlinx.coroutines.test.runTest {
      val store = store()
      val neverPublishedId = newId()

      store.recordInstall(neverPublishedId)

      store.countsFor(listOf(neverPublishedId)) shouldBe mapOf(neverPublishedId to 1L)
    }

  @Test
  fun `concurrent recordInstall calls for the same id never lose an increment`() =
    kotlinx.coroutines.test.runTest {
      val store = store()
      val id = newId()
      val concurrency = 8

      coroutineScope {
        (1..concurrency)
          .map { async(Dispatchers.IO) { store.recordInstall(id) } }
          .map { it.await() }
      }

      store.countsFor(listOf(id)) shouldBe mapOf(id to concurrency.toLong())
    }
}
