package proj.memorchess.axl.server.routes

import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain as stringShouldContain
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation as ServerContentNegotiation
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.time.Instant
import kotlinx.coroutines.runBlocking
import proj.memorchess.axl.core.data.repertoire.RepertoireColor
import proj.memorchess.axl.core.data.repertoire.RepertoireDescriptor
import proj.memorchess.axl.core.data.repertoire.RepertoireManifest
import proj.memorchess.axl.core.sync.SYNC_JSON
import proj.memorchess.axl.server.db.PostgresTestDb
import proj.memorchess.axl.server.repertoire.InMemoryRepertoireBlobStore
import proj.memorchess.axl.server.repertoire.PublishOutcome
import proj.memorchess.axl.server.repertoire.RepertoireCatalogPage
import proj.memorchess.axl.server.repertoire.RepertoireRow
import proj.memorchess.axl.server.repertoire.RepertoireStore

class TestRepertoireRoutes {

  private val now = Instant.fromEpochSeconds(1_700_000_000)

  private fun newId(): String = java.util.UUID.randomUUID().toString()

  private fun pgn(move: String = "e4") =
    "[Event \"${java.util.UUID.randomUUID()}\"]\n[Result \"*\"]\n\n1. $move *"

  private fun app(store: RepertoireStore, block: suspend (HttpClient) -> Unit) = testApplication {
    application {
      install(ServerContentNegotiation) { json(SYNC_JSON) }
      repertoireModule(store)
    }
    block(createClient { install(ContentNegotiation) { json(SYNC_JSON) } })
  }

  private fun newStore() = RepertoireStore(PostgresTestDb.dataSource(), InMemoryRepertoireBlobStore())

  @Test
  fun `manifest json lists only published repertoires with schemaVersion 1`() {
    val store = newStore()
    val id = newId()
    runBlocking { store.publish("author-1", id, "T", "D", "white", pgn(), now) }

    app(store) { client ->
      val response = client.get("/v1/repertoires/manifest.json")

      response.status shouldBe HttpStatusCode.OK
      val manifest = SYNC_JSON.decodeFromString<RepertoireManifest>(response.bodyAsText())
      manifest.schemaVersion shouldBe 1
      manifest.repertoires.map { it.id } shouldContain id
    }
  }

  @Test
  fun `manifest json omits unlisted and removed repertoires`() {
    val store = newStore()
    val unlisted = newId()
    val removed = newId()
    runBlocking {
      store.publish("author-1", unlisted, "T", "D", "white", pgn(), now)
      store.setStatus(unlisted, "unlisted")
      store.publish("author-1", removed, "T", "D", "white", pgn(), now)
      store.remove("author-1", removed)
    }

    app(store) { client ->
      val response = client.get("/v1/repertoires/manifest.json")

      val manifest = SYNC_JSON.decodeFromString<RepertoireManifest>(response.bodyAsText())
      val ids = manifest.repertoires.map { it.id }
      (unlisted in ids) shouldBe false
      (removed in ids) shouldBe false
    }
  }

  @Test
  fun `pgn route serves the stored bytes for a known hash`() {
    val store = newStore()
    val id = newId()
    val published =
      runBlocking { store.publish("author-1", id, "T", "D", "white", pgn("e4"), now) }
        as PublishOutcome.Published

    app(store) { client ->
      val response = client.get("/v1/repertoires/${published.row.file()}")

      response.status shouldBe HttpStatusCode.OK
      response.bodyAsText() stringShouldContain "e4"
    }
  }

  @Test
  fun `pgn route answers 404 for an unknown hash`() {
    app(newStore()) { client ->
      client.get("/v1/repertoires/pgn/does-not-exist.pgn").status shouldBe HttpStatusCode.NotFound
    }
  }

  @Test
  fun `pgn route sets a far future immutable cache header`() {
    val store = newStore()
    val id = newId()
    val published =
      runBlocking { store.publish("author-1", id, "T", "D", "white", pgn(), now) }
        as PublishOutcome.Published

    app(store) { client ->
      val response = client.get("/v1/repertoires/${published.row.file()}")

      response.headers[HttpHeaders.CacheControl] shouldBe "public, max-age=31536000, immutable"
    }
  }

  @Test
  fun `manifest and list routes set a short ttl cache header`() {
    app(newStore()) { client ->
      client.get("/v1/repertoires/manifest.json").headers[HttpHeaders.CacheControl] shouldBe
        "public, max-age=60"
      client.get("/v1/repertoires").headers[HttpHeaders.CacheControl] shouldBe "public, max-age=60"
    }
  }

  @Test
  fun `list route paginates with cursor and limit and clamps an oversized limit`() {
    val store = newStore()
    val prefix = newId()
    val ids = listOf("$prefix-1", "$prefix-2", "$prefix-3")
    runBlocking { ids.forEach { store.publish("author-1", it, "T", "D", "white", pgn(), now) } }

    app(store) { client ->
      val response = client.get("/v1/repertoires?cursor=$prefix&limit=2")

      response.status shouldBe HttpStatusCode.OK
      val page = SYNC_JSON.decodeFromString<RepertoireCatalogPage>(response.bodyAsText())
      page.repertoires.map { it.id } shouldBe ids.take(2)
      page.nextCursor shouldBe ids[1]

      // A limit far above MAX_CATALOG_LIMIT must be silently clamped, never refused.
      val clamped = client.get("/v1/repertoires?cursor=$prefix&limit=1000000")
      clamped.status shouldBe HttpStatusCode.OK
    }
  }

  @Test
  fun `list route rejects a non numeric limit with 400`() {
    app(newStore()) { client ->
      client.get("/v1/repertoires?limit=nope").status shouldBe HttpStatusCode.BadRequest
    }
  }

  @Test
  fun `get by id returns the repertoire metadata`() {
    val store = newStore()
    val id = newId()
    runBlocking { store.publish("author-1", id, "Title", "Desc", "black", pgn(), now) }

    app(store) { client ->
      val response = client.get("/v1/repertoires/$id")

      response.status shouldBe HttpStatusCode.OK
      val descriptor = SYNC_JSON.decodeFromString<RepertoireDescriptor>(response.bodyAsText())
      descriptor.name shouldBe "Title"
      descriptor.color shouldBe RepertoireColor.BLACK
    }
  }

  @Test
  fun `get by id answers 404 for a removed repertoire`() {
    val store = newStore()
    val id = newId()
    runBlocking {
      store.publish("author-1", id, "T", "D", "white", pgn(), now)
      store.remove("author-1", id)
    }

    app(store) { client -> client.get("/v1/repertoires/$id").status shouldBe HttpStatusCode.NotFound }
  }

  @Test
  fun `get by id answers 404 for an unknown id`() {
    app(newStore()) { client ->
      client.get("/v1/repertoires/${newId()}").status shouldBe HttpStatusCode.NotFound
    }
  }

  @Test
  fun `anonymous responses are byte identical with and without a bearer token present`() {
    val store = newStore()
    val id = newId()
    runBlocking { store.publish("author-1", id, "T", "D", "white", pgn(), now) }

    app(store) { client ->
      val withoutToken = client.get("/v1/repertoires/$id").bodyAsText()
      val withToken =
        client.get("/v1/repertoires/$id") { header(HttpHeaders.Authorization, "Bearer garbage") }
          .bodyAsText()

      withoutToken shouldBe withToken
    }
  }
}

private fun RepertoireRow.file(): String = "pgn/$payloadSha256.pgn"
