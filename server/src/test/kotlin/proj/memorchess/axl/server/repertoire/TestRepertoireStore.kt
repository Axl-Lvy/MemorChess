package proj.memorchess.axl.server.repertoire

import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlin.test.Test
import kotlin.time.Instant
import proj.memorchess.axl.server.db.PostgresTestDb

internal class TestRepertoireStore {

  private val now = Instant.fromEpochSeconds(1_700_000_000)

  // A random id, unique per call and unrelated (in sort order) to any other test's ids: the
  // repertoire table is global with no per-test user scoping, since the catalog is anonymous by
  // design, so this is what keeps one test's rows from leaking into another's assertions.
  private fun newId(): String = java.util.UUID.randomUUID().toString()

  // Fresh per test method (a new instance is created for every @Test), so the per author quota
  // counted against the shared table never accumulates across tests the way a literal
  // "author-1" would.
  private fun newAuthor(): String = "author-${java.util.UUID.randomUUID()}"

  private val author1 = newAuthor()
  private val author2 = newAuthor()

  private fun store(
    maxPayloadBytes: Int = MAX_REPERTOIRE_PAYLOAD_BYTES,
    maxMoves: Int = MAX_REPERTOIRE_MOVES,
    maxRepertoiresPerUser: Int = MAX_REPERTOIRES_PER_USER,
    maxTotalPayloadBytesPerUser: Long = MAX_TOTAL_PAYLOAD_BYTES_PER_USER,
    blobs: InMemoryRepertoireBlobStore = InMemoryRepertoireBlobStore(),
  ) =
    RepertoireStore(
      PostgresTestDb.dataSource(),
      blobs,
      maxPayloadBytes,
      maxMoves,
      maxRepertoiresPerUser,
      maxTotalPayloadBytesPerUser,
    )

  // Each call is byte-unique (random Event tag) unless the caller reuses the returned string, so
  // two unrelated tests' payloads never collide on sha256 in the shared blob-refcount checks.
  private fun pgn(move: String = "e4") =
    "[Event \"${java.util.UUID.randomUUID()}\"]\n[Result \"*\"]\n\n1. $move *"

  @Test
  fun `publishing a new repertoire stores it at version 1 and stores the blob`() = kotlinx.coroutines.test.runTest {
    val store = store()
    val id = newId()

    val outcome =
      store.publish(
        authorId = author1,
        id = id,
        title = "Title",
        description = "Desc",
        side = "white",
        pgn = pgn(),
        now = now,
      )

    outcome.shouldBeInstanceOf<PublishOutcome.Published>()
    outcome.row.version shouldBe 1
    outcome.row.status shouldBe "published"
    outcome.row.moveCount shouldBe 1
    store.readPayload(outcome.row.payloadSha256).shouldNotBeNull()
  }

  @Test
  fun `publishing again for the same author creates version 2 and keeps version 1 untouched`() =
    kotlinx.coroutines.test.runTest {
      val store = store()
      val id = newId()
      store.publish(author1, id, "T", "D", "white", pgn("e4"), now)

      val second = store.publish(author1, id, "T2", "D2", "black", pgn("d4"), now)

      second.shouldBeInstanceOf<PublishOutcome.Published>()
      second.row.version shouldBe 2
      second.row.title shouldBe "T2"
    }

  @Test
  fun `publishing for an id owned by a different author is forbidden`() = kotlinx.coroutines.test.runTest {
    val store = store()
    val id = newId()
    store.publish(author1, id, "T", "D", "white", pgn(), now)

    val outcome = store.publish(author2, id, "T", "D", "white", pgn("d4"), now)

    outcome shouldBe PublishOutcome.Forbidden
  }

  @Test
  fun `publishing rejects a pgn that does not parse`() = kotlinx.coroutines.test.runTest {
    val outcome = store().publish(author1, newId(), "T", "D", "white", "1. e4 (1... e5", now)

    outcome.shouldBeInstanceOf<PublishOutcome.InvalidPayload>()
  }

  @Test
  fun `publishing rejects a pgn with an illegal move`() = kotlinx.coroutines.test.runTest {
    val illegal = "[Event \"T\"]\n[Result \"*\"]\n\n1. e4 e5 2. Ke2 Ke7 3. Qh5 Qh4 4. Bxb5 *"

    val outcome = store().publish(author1, newId(), "T", "D", "white", illegal, now)

    outcome.shouldBeInstanceOf<PublishOutcome.InvalidPayload>()
  }

  @Test
  fun `publishing rejects a payload one byte over the cap`() = kotlinx.coroutines.test.runTest {
    val content = pgn()
    val cap = content.encodeToByteArray().size - 1

    val outcome =
      store(maxPayloadBytes = cap).publish(author1, newId(), "T", "D", "white", content, now)

    outcome.shouldBeInstanceOf<PublishOutcome.PayloadTooLarge>()
  }

  @Test
  fun `publishing accepts a payload of exactly the cap`() = kotlinx.coroutines.test.runTest {
    val content = pgn()
    val cap = content.encodeToByteArray().size

    val outcome =
      store(maxPayloadBytes = cap).publish(author1, newId(), "T", "D", "white", content, now)

    outcome.shouldBeInstanceOf<PublishOutcome.Published>()
  }

  @Test
  fun `publishing rejects an id shape that would break the client install store`() =
    kotlinx.coroutines.test.runTest {
      val outcome = store().publish(author1, "bad,id", "T", "D", "white", pgn(), now)

      outcome.shouldBeInstanceOf<PublishOutcome.InvalidPayload>()
    }

  @Test
  fun `publishing rejects an id that is too short`() = kotlinx.coroutines.test.runTest {
    val outcome = store().publish(author1, "ab", "T", "D", "white", pgn(), now)

    outcome.shouldBeInstanceOf<PublishOutcome.InvalidPayload>()
  }

  @Test
  fun `publishing the one over the repertoire count quota is refused`() = kotlinx.coroutines.test.runTest {
    val store = store(maxRepertoiresPerUser = 2)
    val author = "author-quota-${System.nanoTime()}"
    store.publish(author, newId(), "T", "D", "white", pgn("e4"), now)
    store.publish(author, newId(), "T", "D", "white", pgn("d4"), now)

    val outcome = store.publish(author, newId(), "T", "D", "white", pgn("c4"), now)

    outcome.shouldBeInstanceOf<PublishOutcome.QuotaExceeded>()
  }

  @Test
  fun `publishing accepts exactly maxRepertoiresPerUser repertoires`() = kotlinx.coroutines.test.runTest {
    val store = store(maxRepertoiresPerUser = 2)
    val author = "author-quota-ok-${System.nanoTime()}"
    store.publish(author, newId(), "T", "D", "white", pgn("e4"), now)

    val outcome = store.publish(author, newId(), "T", "D", "white", pgn("d4"), now)

    outcome.shouldBeInstanceOf<PublishOutcome.Published>()
  }

  @Test
  fun `publishing a payload that pushes the author past the total byte quota is refused`() =
    kotlinx.coroutines.test.runTest {
      val first = pgn("e4")
      val cap = first.encodeToByteArray().size.toLong()
      val store = store(maxTotalPayloadBytesPerUser = cap)
      val author = "author-bytes-${System.nanoTime()}"
      store.publish(author, newId(), "T", "D", "white", first, now)

      val outcome = store.publish(author, newId(), "T", "D", "white", pgn("d4"), now)

      outcome.shouldBeInstanceOf<PublishOutcome.QuotaExceeded>()
    }

  @Test
  fun `republishing the same id does not count its own prior version against the byte quota`() =
    kotlinx.coroutines.test.runTest {
      val first = pgn("e4")
      val cap = first.encodeToByteArray().size.toLong()
      val store = store(maxTotalPayloadBytesPerUser = cap)
      val author = "author-republish-${System.nanoTime()}"
      val id = newId()
      store.publish(author, id, "T", "D", "white", first, now)

      val outcome = store.publish(author, id, "T2", "D2", "white", pgn("d4"), now)

      outcome.shouldBeInstanceOf<PublishOutcome.Published>()
      outcome.row.version shouldBe 2
    }

  @Test
  fun `remove sets status to removed and get returns null afterward`() = kotlinx.coroutines.test.runTest {
    val store = store()
    val id = newId()
    store.publish(author1, id, "T", "D", "white", pgn(), now)

    val outcome = store.remove(author1, id)

    outcome shouldBe RemoveOutcome.Removed
    store.get(id).shouldBeNull()
  }

  @Test
  fun `remove by a non author is forbidden and leaves the row published`() = kotlinx.coroutines.test.runTest {
    val store = store()
    val id = newId()
    store.publish(author1, id, "T", "D", "white", pgn(), now)

    val outcome = store.remove(author2, id)

    outcome shouldBe RemoveOutcome.Forbidden
    store.get(id).shouldNotBeNull()
  }

  @Test
  fun `remove of an unknown id returns NotFound`() = kotlinx.coroutines.test.runTest {
    val outcome = store().remove(author1, newId())

    outcome shouldBe RemoveOutcome.NotFound
  }

  @Test
  fun `remove deletes the blob when no other version references its hash`() = kotlinx.coroutines.test.runTest {
    val blobs = InMemoryRepertoireBlobStore()
    val store = store(blobs = blobs)
    val id = newId()
    val published = store.publish(author1, id, "T", "D", "white", pgn(), now)
    val sha256 = (published as PublishOutcome.Published).row.payloadSha256

    store.remove(author1, id)

    blobs.get(sha256).shouldBeNull()
  }

  @Test
  fun `remove keeps the blob when another version still references the same hash`() =
    kotlinx.coroutines.test.runTest {
      val blobs = InMemoryRepertoireBlobStore()
      val store = store(blobs = blobs)
      val content = pgn()
      val idA = newId()
      val idB = newId()
      val publishedA = store.publish(author1, idA, "T", "D", "white", content, now)
      store.publish(author2, idB, "T", "D", "white", content, now)
      val sha256 = (publishedA as PublishOutcome.Published).row.payloadSha256

      store.remove(author1, idA)

      blobs.get(sha256).shouldNotBeNull()
    }

  @Test
  fun `setStatus moves a repertoire to unlisted and get still returns it`() = kotlinx.coroutines.test.runTest {
    val store = store()
    val id = newId()
    store.publish(author1, id, "T", "D", "white", pgn(), now)

    val outcome = store.setStatus(id, "unlisted")

    outcome.shouldBeInstanceOf<SetStatusOutcome.Updated>()
    outcome.row.status shouldBe "unlisted"
    store.get(id)?.status shouldBe "unlisted"
  }

  @Test
  fun `setStatus to removed deletes the blob under the same refcount rule`() = kotlinx.coroutines.test.runTest {
    val blobs = InMemoryRepertoireBlobStore()
    val store = store(blobs = blobs)
    val id = newId()
    val published = store.publish(author1, id, "T", "D", "white", pgn(), now)
    val sha256 = (published as PublishOutcome.Published).row.payloadSha256

    store.setStatus(id, "removed")

    blobs.get(sha256).shouldBeNull()
    store.get(id).shouldBeNull()
  }

  @Test
  fun `setStatus on an unknown id returns NotFound`() = kotlinx.coroutines.test.runTest {
    val outcome = store().setStatus(newId(), "unlisted")

    outcome shouldBe SetStatusOutcome.NotFound
  }

  @Test
  fun `get returns null for a status of removed`() = kotlinx.coroutines.test.runTest {
    val store = store()
    val id = newId()
    store.publish(author1, id, "T", "D", "white", pgn(), now)
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
      store.publish(author, published, "T", "D", "white", pgn("e4"), now)
      store.publish(author, unlisted, "T", "D", "white", pgn("d4"), now)
      store.setStatus(unlisted, "unlisted")
      store.publish(author, removed, "T", "D", "white", pgn("c4"), now)
      store.remove(author, removed)

      // The table is global (no per-test scoping — the catalog is anonymous by design), so this
      // checks containment rather than exact equality against the whole table's contents.
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
      ids.forEach { store.publish(author, it, "T", "D", "white", pgn("e4"), now) }

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
      store.publish(author, id, "T1", "D", "white", pgn("e4"), now)
      store.publish(author, id, "T2", "D", "white", pgn("d4"), now)

      val page = store.listPublished(cursor = null, limit = 100)

      page.rows.count { it.id == id } shouldBe 1
      page.rows.first { it.id == id }.title shouldBe "T2"
    }

  @Test
  fun `allPublished returns every published repertoire unpaginated`() = kotlinx.coroutines.test.runTest {
    val store = store()
    val author = "author-all-${System.nanoTime()}"
    val ids = (1..5).map { newId() }
    ids.forEach { store.publish(author, it, "T", "D", "white", pgn("e4"), now) }

    val all = store.allPublished()

    ids.forEach { id -> all.map { it.id } shouldContain id }
  }

  @Test
  fun `readPayload returns the stored bytes for a known hash and null for an unknown one`() =
    kotlinx.coroutines.test.runTest {
      val store = store()
      val id = newId()
      val published = store.publish(author1, id, "T", "D", "white", pgn(), now)
      val row = (published as PublishOutcome.Published).row

      store.readPayload(row.payloadSha256).shouldNotBeNull()
      store.readPayload("not-a-real-hash").shouldBeNull()
    }
}
