package proj.memorchess.axl.server.routes

import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain as stringShouldContain
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation as ServerContentNegotiation
import io.ktor.server.testing.testApplication
import java.net.URI
import kotlin.test.Test
import kotlin.time.Instant
import kotlinx.coroutines.runBlocking
import proj.memorchess.axl.core.data.repertoire.RepertoireColor
import proj.memorchess.axl.core.data.repertoire.RepertoireDescriptor
import proj.memorchess.axl.core.data.repertoire.RepertoireManifest
import proj.memorchess.axl.core.sync.ApiError
import proj.memorchess.axl.core.sync.ApiErrorCode
import proj.memorchess.axl.core.sync.SYNC_JSON
import proj.memorchess.axl.server.ServerConfig
import proj.memorchess.axl.server.auth.TEST_AUDIENCE
import proj.memorchess.axl.server.auth.TEST_ISSUER
import proj.memorchess.axl.server.auth.TestJwkProvider
import proj.memorchess.axl.server.auth.TestSigningKey
import proj.memorchess.axl.server.auth.installJwtAuth
import proj.memorchess.axl.server.db.PostgresTestDb
import proj.memorchess.axl.server.repertoire.InMemoryRepertoireBlobStore
import proj.memorchess.axl.server.repertoire.PublishOutcome
import proj.memorchess.axl.server.repertoire.PublishRepertoireRequest
import proj.memorchess.axl.server.repertoire.RepertoireCatalogPage
import proj.memorchess.axl.server.repertoire.RepertoireRow
import proj.memorchess.axl.server.repertoire.RepertoireStatusRequest
import proj.memorchess.axl.server.repertoire.RepertoireStore
import proj.memorchess.axl.server.repertoire.RepertoireValidation

class TestRepertoireRoutes {

  private val now = Instant.fromEpochSeconds(1_700_000_000)

  private val key = TestSigningKey("kid-1")

  private val jwtConfig =
    ServerConfig(
      port = 0,
      jdbcUrl = "unused",
      dbUser = "unused",
      dbPassword = "unused",
      jwtIssuer = TEST_ISSUER,
      jwtAudience = TEST_AUDIENCE,
      jwksUrl = URI("https://issuer.test/jwks.json"),
      r2Endpoint = URI("https://r2.test/"),
      r2Bucket = "unused",
      r2AccessKeyId = "unused",
      r2SecretAccessKey = "unused",
    )

  private fun newId(): String = java.util.UUID.randomUUID().toString()

  private fun pgn(move: String = "e4") =
    "[Event \"${java.util.UUID.randomUUID()}\"]\n[Result \"*\"]\n\n1. $move *"

  // Fresh per test method (a new instance is created for every @Test), so the per author quota
  // counted against the shared table never accumulates across tests the way a literal "author-1"
  // would.
  private fun newAuthor(): String = "author-${java.util.UUID.randomUUID()}"

  private val author1 = newAuthor()
  private val author2 = newAuthor()

  private fun app(store: RepertoireStore, block: suspend (HttpClient) -> Unit) = testApplication {
    application {
      install(ServerContentNegotiation) { json(SYNC_JSON) }
      installJwtAuth(jwtConfig, TestJwkProvider(key))
      repertoireModule(store, clock = { now })
    }
    block(createClient { install(ContentNegotiation) { json(SYNC_JSON) } })
  }

  private fun newStore() =
    RepertoireStore(PostgresTestDb.dataSource(), InMemoryRepertoireBlobStore())

  @Test
  fun `manifest json lists only published repertoires with schemaVersion 1`() {
    val store = newStore()
    val id = newId()
    runBlocking { store.publish(author1, id, "T", "D", "white", pgn(), now) }

    app(store) { client ->
      val response = client.get("/v1/repertoires/manifest.json")

      response.status shouldBe HttpStatusCode.OK
      val manifest = SYNC_JSON.decodeFromString<RepertoireManifest>(response.bodyAsText())
      manifest.schemaVersion shouldBe 1
      manifest.repertoires.map { it.id } shouldContain id
    }
  }

  @Test
  fun `manifest json reports zero downloadCount for a repertoire with no recorded installs`() {
    val store = newStore()
    val id = newId()
    runBlocking { store.publish(author1, id, "T", "D", "white", pgn(), now) }

    app(store) { client ->
      val response = client.get("/v1/repertoires/manifest.json")

      val manifest = SYNC_JSON.decodeFromString<RepertoireManifest>(response.bodyAsText())
      manifest.repertoires.first { it.id == id }.downloadCount shouldBe 0
    }
  }

  @Test
  fun `install route increments downloadCount and reports it back in the manifest`() {
    val store = newStore()
    val id = newId()
    runBlocking { store.publish(author1, id, "T", "D", "white", pgn(), now) }

    app(store) { client ->
      val first = client.post("/v1/repertoires/$id/installs")
      val second = client.post("/v1/repertoires/$id/installs")

      first.status shouldBe HttpStatusCode.NoContent
      second.status shouldBe HttpStatusCode.NoContent
      val manifest =
        SYNC_JSON.decodeFromString<RepertoireManifest>(
          client.get("/v1/repertoires/manifest.json").bodyAsText()
        )
      manifest.repertoires.first { it.id == id }.downloadCount shouldBe 2
    }
  }

  @Test
  fun `install route requires no authentication`() {
    val store = newStore()
    val id = newId()
    runBlocking { store.publish(author1, id, "T", "D", "white", pgn(), now) }

    app(store) { client ->
      // No Authorization header at all, unlike the authenticated publish/delete routes below.
      client.post("/v1/repertoires/$id/installs").status shouldBe HttpStatusCode.NoContent
    }
  }

  @Test
  fun `install route on an unknown id still answers no content, harmlessly recording it`() {
    app(newStore()) { client ->
      client.post("/v1/repertoires/${newId()}/installs").status shouldBe HttpStatusCode.NoContent
    }
  }

  @Test
  fun `install route rejects a malformed id with bad request, never recording it`() {
    app(newStore()) { client ->
      // Contains an uppercase letter, which ID_PATTERN rejects; unlike the "unknown id" case above,
      // this shape is invalid regardless of whether anything with it was ever published.
      client.post("/v1/repertoires/Not-A-Valid-Id/installs").status shouldBe
        HttpStatusCode.BadRequest
    }
  }

  @Test
  fun `manifest json omits unlisted and removed repertoires`() {
    val store = newStore()
    val unlisted = newId()
    val removed = newId()
    runBlocking {
      store.publish(author1, unlisted, "T", "D", "white", pgn(), now)
      store.setStatus(unlisted, "unlisted")
      store.publish(author1, removed, "T", "D", "white", pgn(), now)
      store.remove(author1, removed)
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
      runBlocking { store.publish(author1, id, "T", "D", "white", pgn("e4"), now) }
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
  fun `pgn route answers 404 for a stored blob no live row references`() {
    val blobs = InMemoryRepertoireBlobStore()
    val orphanHash = "a".repeat(64)
    runBlocking { blobs.put(orphanHash, "orphan".encodeToByteArray()) }
    val store = RepertoireStore(PostgresTestDb.dataSource(), blobs)

    app(store) { client ->
      client.get("/v1/repertoires/pgn/$orphanHash.pgn").status shouldBe HttpStatusCode.NotFound
    }
  }

  @Test
  fun `pgn route answers 404 for a removed repertoire's hash, revoking access to the bytes`() {
    val store = newStore()
    val id = newId()
    val published =
      runBlocking { store.publish(author1, id, "T", "D", "white", pgn(), now) }
        as PublishOutcome.Published

    app(store) { client ->
      client.post("/admin/repertoires/$id/status") {
        contentType(ContentType.Application.Json)
        setBody(SYNC_JSON.encodeToString(RepertoireStatusRequest("removed")))
      }

      client.get("/v1/repertoires/${published.row.file()}").status shouldBe HttpStatusCode.NotFound
    }
  }

  @Test
  fun `pgn route sets a far future immutable cache header`() {
    val store = newStore()
    val id = newId()
    val published =
      runBlocking { store.publish(author1, id, "T", "D", "white", pgn(), now) }
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
    runBlocking { ids.forEach { store.publish(author1, it, "T", "D", "white", pgn(), now) } }

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
    runBlocking { store.publish(author1, id, "Title", "Desc", "black", pgn(), now) }

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
      store.publish(author1, id, "T", "D", "white", pgn(), now)
      store.remove(author1, id)
    }

    app(store) { client ->
      client.get("/v1/repertoires/$id").status shouldBe HttpStatusCode.NotFound
    }
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
    runBlocking { store.publish(author1, id, "T", "D", "white", pgn(), now) }

    app(store) { client ->
      val withoutToken = client.get("/v1/repertoires/$id").bodyAsText()
      val withToken =
        client
          .get("/v1/repertoires/$id") { header(HttpHeaders.Authorization, "Bearer garbage") }
          .bodyAsText()

      withoutToken shouldBe withToken
    }
  }

  private fun publishBody(
    id: String,
    title: String = "T",
    description: String = "D",
    side: String = "white",
    pgn: String = pgn(),
  ) =
    SYNC_JSON.encodeToString(
      PublishRepertoireRequest(
        id = id,
        title = title,
        description = description,
        side = side,
        pgn = pgn,
      )
    )

  @Test
  fun `post publishes a new repertoire and returns 201 with its descriptor`() {
    val id = newId()

    app(newStore()) { client ->
      val response =
        client.post("/v1/repertoires") {
          header(HttpHeaders.Authorization, "Bearer ${key.token(subject = author1)}")
          contentType(ContentType.Application.Json)
          setBody(publishBody(id, title = "My Title"))
        }

      response.status shouldBe HttpStatusCode.Created
      val descriptor = SYNC_JSON.decodeFromString<RepertoireDescriptor>(response.bodyAsText())
      descriptor.id shouldBe id
      descriptor.name shouldBe "My Title"
    }
  }

  @Test
  fun `post creates a second version when the same author republishes the same id`() {
    val id = newId()

    app(newStore()) { client ->
      val token = key.token(subject = author1)
      client.post("/v1/repertoires") {
        header(HttpHeaders.Authorization, "Bearer $token")
        contentType(ContentType.Application.Json)
        setBody(publishBody(id, title = "First"))
      }

      val second =
        client.post("/v1/repertoires") {
          header(HttpHeaders.Authorization, "Bearer $token")
          contentType(ContentType.Application.Json)
          setBody(publishBody(id, title = "Second"))
        }

      second.status shouldBe HttpStatusCode.Created
      SYNC_JSON.decodeFromString<RepertoireDescriptor>(second.bodyAsText()).name shouldBe "Second"
    }
  }

  @Test
  fun `post is forbidden when a different author republishes an existing id`() {
    val id = newId()

    app(newStore()) { client ->
      client.post("/v1/repertoires") {
        header(HttpHeaders.Authorization, "Bearer ${key.token(subject = author1)}")
        contentType(ContentType.Application.Json)
        setBody(publishBody(id))
      }

      val response =
        client.post("/v1/repertoires") {
          header(HttpHeaders.Authorization, "Bearer ${key.token(subject = author2)}")
          contentType(ContentType.Application.Json)
          setBody(publishBody(id))
        }

      response.status shouldBe HttpStatusCode.Forbidden
    }
  }

  @Test
  fun `post is forbidden when the author republishes an id a moderator removed`() {
    val store = newStore()
    val id = newId()
    runBlocking {
      store.publish(author1, id, "T", "D", "white", pgn(), now)
      store.setStatus(id, "removed")
    }

    app(store) { client ->
      val response =
        client.post("/v1/repertoires") {
          header(HttpHeaders.Authorization, "Bearer ${key.token(subject = author1)}")
          contentType(ContentType.Application.Json)
          setBody(publishBody(id))
        }

      response.status shouldBe HttpStatusCode.Forbidden
    }
  }

  @Test
  fun `post rejects an unparseable pgn with invalid_pgn`() {
    app(newStore()) { client ->
      val response =
        client.post("/v1/repertoires") {
          header(HttpHeaders.Authorization, "Bearer ${key.token(subject = author1)}")
          contentType(ContentType.Application.Json)
          setBody(publishBody(newId(), pgn = "1. e4 (1... e5"))
        }

      response.status shouldBe HttpStatusCode.BadRequest
      SYNC_JSON.decodeFromString<ApiError>(response.bodyAsText()).code shouldBe
        ApiErrorCode.INVALID_PGN
    }
  }

  @Test
  fun `post rejects an illegal move with invalid_pgn`() {
    val illegal = "[Event \"T\"]\n[Result \"*\"]\n\n1. e4 e5 2. Ke2 Ke7 3. Qh5 Qh4 4. Bxb5 *"

    app(newStore()) { client ->
      val response =
        client.post("/v1/repertoires") {
          header(HttpHeaders.Authorization, "Bearer ${key.token(subject = author1)}")
          contentType(ContentType.Application.Json)
          setBody(publishBody(newId(), pgn = illegal))
        }

      response.status shouldBe HttpStatusCode.BadRequest
    }
  }

  @Test
  fun `post maps an unexpected validation failure to 500 internal, not 400 invalid_pgn`() {
    val store =
      RepertoireStore(
        dataSource = PostgresTestDb.dataSource(),
        blobs = InMemoryRepertoireBlobStore(),
        validate = { _, _, _ -> RepertoireValidation.Failed("boom") },
      )

    app(store) { client ->
      val response =
        client.post("/v1/repertoires") {
          header(HttpHeaders.Authorization, "Bearer ${key.token(subject = author1)}")
          contentType(ContentType.Application.Json)
          setBody(publishBody(newId()))
        }

      response.status shouldBe HttpStatusCode.InternalServerError
      SYNC_JSON.decodeFromString<ApiError>(response.bodyAsText()).code shouldBe
        ApiErrorCode.INTERNAL
    }
  }

  @Test
  fun `post without a bearer token is unauthorized`() {
    app(newStore()) { client ->
      val response =
        client.post("/v1/repertoires") {
          contentType(ContentType.Application.Json)
          setBody(publishBody(newId()))
        }

      response.status shouldBe HttpStatusCode.Unauthorized
    }
  }

  @Test
  fun `post rejects an unknown side value with bad_request`() {
    app(newStore()) { client ->
      val response =
        client.post("/v1/repertoires") {
          header(HttpHeaders.Authorization, "Bearer ${key.token(subject = author1)}")
          contentType(ContentType.Application.Json)
          setBody(publishBody(newId(), side = "purple"))
        }

      response.status shouldBe HttpStatusCode.BadRequest
    }
  }

  @Test
  fun `delete removes a repertoire the caller authored`() {
    val store = newStore()
    val id = newId()
    runBlocking { store.publish(author1, id, "T", "D", "white", pgn(), now) }

    app(store) { client ->
      val response =
        client.delete("/v1/repertoires/$id") {
          header(HttpHeaders.Authorization, "Bearer ${key.token(subject = author1)}")
        }

      response.status shouldBe HttpStatusCode.NoContent
    }
  }

  @Test
  fun `delete by a non author is forbidden`() {
    val store = newStore()
    val id = newId()
    runBlocking { store.publish(author1, id, "T", "D", "white", pgn(), now) }

    app(store) { client ->
      val response =
        client.delete("/v1/repertoires/$id") {
          header(HttpHeaders.Authorization, "Bearer ${key.token(subject = author2)}")
        }

      response.status shouldBe HttpStatusCode.Forbidden
    }
  }

  @Test
  fun `delete of an unknown id is not_found`() {
    app(newStore()) { client ->
      val response =
        client.delete("/v1/repertoires/${newId()}") {
          header(HttpHeaders.Authorization, "Bearer ${key.token(subject = author1)}")
        }

      response.status shouldBe HttpStatusCode.NotFound
    }
  }

  @Test
  fun `delete without a bearer token is unauthorized`() {
    app(newStore()) { client ->
      client.delete("/v1/repertoires/${newId()}").status shouldBe HttpStatusCode.Unauthorized
    }
  }

  @Test
  fun `admin status change succeeds`() {
    val store = newStore()
    val id = newId()
    runBlocking { store.publish(author1, id, "T", "D", "white", pgn(), now) }

    app(store) { client ->
      val response =
        client.post("/admin/repertoires/$id/status") {
          contentType(ContentType.Application.Json)
          setBody(SYNC_JSON.encodeToString(RepertoireStatusRequest("unlisted")))
        }

      response.status shouldBe HttpStatusCode.OK
    }
  }

  @Test
  fun `admin status change on an unknown id is not_found`() {
    app(newStore()) { client ->
      val response =
        client.post("/admin/repertoires/${newId()}/status") {
          contentType(ContentType.Application.Json)
          setBody(SYNC_JSON.encodeToString(RepertoireStatusRequest("unlisted")))
        }

      response.status shouldBe HttpStatusCode.NotFound
    }
  }

  @Test
  fun `removing via admin status makes the repertoire disappear from the public list`() {
    val store = newStore()
    val id = newId()
    runBlocking { store.publish(author1, id, "T", "D", "white", pgn(), now) }

    app(store) { client ->
      client.post("/admin/repertoires/$id/status") {
        contentType(ContentType.Application.Json)
        setBody(SYNC_JSON.encodeToString(RepertoireStatusRequest("removed")))
      }

      client.get("/v1/repertoires/$id").status shouldBe HttpStatusCode.NotFound
    }
  }
}

private fun RepertoireRow.file(): String = "pgn/$payloadSha256.pgn"
