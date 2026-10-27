package proj.memorchess.axl.core.data.repertoire

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlinx.coroutines.test.runTest
import proj.memorchess.axl.core.engine.GameEngine

class TestRepertoireCatalogClient {

  private val manifestJson =
    """
    {
      "schemaVersion": 2,
      "repertoires": [
        {
          "id": "london-system-white",
          "name": "London System",
          "color": "white",
          "description": "A solid system for White.",
          "moveCount": 73,
          "version": 1,
          "futureField": "ignored"
        },
        {
          "id": "caro-kann-black",
          "name": "Caro-Kann",
          "color": "black",
          "description": "A rock solid defense for Black.",
          "moveCount": 74,
          "version": 3
        }
      ],
      "futureTopLevelField": {"nested": true}
    }
    """
      .trimIndent()

  private val e4 =
    GameEngine().let { engine ->
      val from = engine.toPositionKey().value
      engine.playSanMove("e4")
      RepertoireMove(from, "e4", engine.toPositionKey().value, isGood = true)
    }

  private val validMoves =
    """{"moves":[{"from":"${e4.from}","move":"e4","to":"${e4.to}","isGood":true}]}"""

  private fun clientRespondingWith(
    content: String,
    status: HttpStatusCode = HttpStatusCode.OK,
  ): RepertoireCatalogClient {
    val engine = MockEngine { _ ->
      respond(
        content = content,
        status = status,
        headers = headersOf("Content-Type", "text/plain; charset=utf-8"),
      )
    }
    return RepertoireCatalogClient(httpClient = HttpClient(engine), baseUrl = TEST_BASE_URL)
  }

  private fun clientThrowing(): RepertoireCatalogClient {
    val engine = MockEngine { _ -> throw RuntimeException("connection refused") }
    return RepertoireCatalogClient(httpClient = HttpClient(engine), baseUrl = TEST_BASE_URL)
  }

  @Test
  fun fetchManifestParsesValidManifestAndToleratesUnknownFields() = runTest {
    val client = clientRespondingWith(manifestJson)

    val result = client.fetchManifest()

    result.shouldBeInstanceOf<CatalogResult.Ok<RepertoireManifest>>()
    val manifest = result.value
    manifest.schemaVersion shouldBe 2
    manifest.repertoires shouldHaveSize 2
    manifest.repertoires[0].id shouldBe "london-system-white"
    manifest.repertoires[0].color shouldBe RepertoireColor.WHITE
    manifest.repertoires[0].moveCount shouldBe 73
    manifest.repertoires[1].color shouldBe RepertoireColor.BLACK
    manifest.repertoires[1].version shouldBe 3
  }

  @Test
  fun fetchManifestAcceptsZeroAndLargeMoveCounts() = runTest {
    val edgeManifest =
      """
      {
        "schemaVersion": 2,
        "repertoires": [
          {"id": "empty", "name": "Empty", "color": "white", "description": "",
           "moveCount": 0, "version": 1},
          {"id": "huge", "name": "Huge", "color": "black", "description": "",
           "moveCount": 2147483647, "version": 1}
        ]
      }
      """
        .trimIndent()
    val client = clientRespondingWith(edgeManifest)

    val result = client.fetchManifest()

    result.shouldBeInstanceOf<CatalogResult.Ok<RepertoireManifest>>()
    result.value.repertoires[0].moveCount shouldBe 0
    result.value.repertoires[1].moveCount shouldBe Int.MAX_VALUE
  }

  @Test
  fun fetchManifestNotFoundReturnsHttpError() = runTest {
    val client = clientRespondingWith("Not Found", HttpStatusCode.NotFound)

    val result = client.fetchManifest()

    result shouldBe CatalogResult.HttpError(404)
  }

  @Test
  fun fetchManifestNetworkFailureReturnsNetworkError() = runTest {
    val client = clientThrowing()

    val result = client.fetchManifest()

    result.shouldBeInstanceOf<CatalogResult.NetworkError>()
  }

  @Test
  fun fetchManifestMalformedJsonReturnsMalformedManifest() = runTest {
    val client = clientRespondingWith("{\"schemaVersion\": 1, \"repertoires\": [")

    val result = client.fetchManifest()

    result.shouldBeInstanceOf<CatalogResult.MalformedManifest>()
  }

  @Test
  fun fetchManifestUnknownColorReturnsMalformedManifest() = runTest {
    val badColor =
      """
      {
        "schemaVersion": 2,
        "repertoires": [
          {"id": "x", "name": "X", "color": "purple", "description": "",
           "moveCount": 1, "version": 1}
        ]
      }
      """
        .trimIndent()
    val client = clientRespondingWith(badColor)

    val result = client.fetchManifest()

    result.shouldBeInstanceOf<CatalogResult.MalformedManifest>()
  }

  @Test
  fun fetchManifestUnsupportedSchemaVersionReturnsMalformedManifest() = runTest {
    val client = clientRespondingWith("{\"schemaVersion\": 1, \"repertoires\": []}")

    val result = client.fetchManifest()

    result.shouldBeInstanceOf<CatalogResult.MalformedManifest>()
  }

  @Test
  fun fetchMovesDecodesTheMoveList() = runTest {
    val client = clientRespondingWith(validMoves)

    val result = client.fetchMoves("london-system-white", 3)

    result shouldBe CatalogResult.Ok(listOf(e4))
  }

  @Test
  fun fetchMovesRequestsTheVersionsMovesPath() = runTest {
    var requestedUrl: String? = null
    val engine = MockEngine { request ->
      requestedUrl = request.url.toString()
      respond(content = validMoves, status = HttpStatusCode.OK)
    }
    val client = RepertoireCatalogClient(httpClient = HttpClient(engine), baseUrl = TEST_BASE_URL)

    client.fetchMoves("london-system-white", 3)

    requestedUrl shouldBe "$TEST_BASE_URL/london-system-white/versions/3/moves"
  }

  @Test
  fun fetchMovesNotFoundReturnsHttpError() = runTest {
    val client = clientRespondingWith("Not Found", HttpStatusCode.NotFound)

    val result = client.fetchMoves("missing", 1)

    result shouldBe CatalogResult.HttpError(404)
  }

  @Test
  fun fetchMovesNetworkFailureReturnsNetworkError() = runTest {
    val client = clientThrowing()

    val result = client.fetchMoves("london-system-white", 1)

    result.shouldBeInstanceOf<CatalogResult.NetworkError>()
  }

  @Test
  fun fetchMovesUndecodableBodyReturnsMalformedRepertoire() = runTest {
    val client = clientRespondingWith("1. e4 e5 *")

    val result = client.fetchMoves("london-system-white", 1)

    result.shouldBeInstanceOf<CatalogResult.MalformedRepertoire>()
  }

  @Test
  fun fetchMovesWithoutAnyMoveReturnsMalformedRepertoire() = runTest {
    val client = clientRespondingWith("{\"moves\":[]}")

    val result = client.fetchMoves("london-system-white", 1)

    result.shouldBeInstanceOf<CatalogResult.MalformedRepertoire>()
  }

  @Test
  fun fetchMovesReportsDownloadProgressEndingAtOne() = runTest {
    val engine = MockEngine { _ ->
      respond(
        content = validMoves,
        status = HttpStatusCode.OK,
        headers =
          headersOf(
            "Content-Type" to listOf("application/json"),
            "Content-Length" to listOf(validMoves.encodeToByteArray().size.toString()),
          ),
      )
    }
    val client = RepertoireCatalogClient(httpClient = HttpClient(engine), baseUrl = TEST_BASE_URL)
    val reported = mutableListOf<Float>()

    val result = client.fetchMoves("london-system-white", 1, onProgress = reported::add)

    result.shouldBeInstanceOf<CatalogResult.Ok<List<RepertoireMove>>>()
    reported shouldHaveSize 1
    reported.last() shouldBe 1f
  }

  @Test
  fun fetchMovesWithoutContentLengthReportsNoProgress() = runTest {
    val client = clientRespondingWith(validMoves)
    val reported = mutableListOf<Float>()

    val result = client.fetchMoves("london-system-white", 1, onProgress = reported::add)

    result.shouldBeInstanceOf<CatalogResult.Ok<List<RepertoireMove>>>()
    reported shouldBe emptyList()
  }

  @Test
  fun fetchMovesWithZeroContentLengthReportsNoProgress() = runTest {
    // An honestly empty body, Content-Length included, is the boundary case that would otherwise
    // divide by zero when computing bytesSentTotal / contentLength.
    val engine = MockEngine { _ ->
      respond(
        content = "",
        status = HttpStatusCode.OK,
        headers =
          headersOf(
            "Content-Type" to listOf("application/json"),
            "Content-Length" to listOf("0"),
          ),
      )
    }
    val client = RepertoireCatalogClient(httpClient = HttpClient(engine), baseUrl = TEST_BASE_URL)
    val reported = mutableListOf<Float>()

    client.fetchMoves("london-system-white", 1, onProgress = reported::add)

    // Nothing to divide by: guarded rather than reporting a NaN or infinite fraction.
    reported shouldBe emptyList()
  }

  @Test
  fun bypassingTheCacheSendsNoCache() = runTest {
    var cacheControl: String? = null
    val engine = MockEngine { request ->
      cacheControl = request.headers[HttpHeaders.CacheControl]
      respond(content = manifestJson, status = HttpStatusCode.OK)
    }
    val client = RepertoireCatalogClient(httpClient = HttpClient(engine), baseUrl = TEST_BASE_URL)

    client.fetchManifest(bypassCache = true)

    cacheControl shouldBe "no-cache"
  }

  @Test
  fun aNormalFetchSendsNoCacheControl() = runTest {
    var cacheControl: String? = "unset"
    val engine = MockEngine { request ->
      cacheControl = request.headers[HttpHeaders.CacheControl]
      respond(content = manifestJson, status = HttpStatusCode.OK)
    }
    val client = RepertoireCatalogClient(httpClient = HttpClient(engine), baseUrl = TEST_BASE_URL)

    client.fetchManifest()

    cacheControl shouldBe null
  }

  @Test
  fun reportInstallPostsToTheIdsInstallsPath() = runTest {
    var requestedUrl: String? = null
    var requestedMethod: HttpMethod? = null
    val engine = MockEngine { request ->
      requestedUrl = request.url.toString()
      requestedMethod = request.method
      respond(content = "", status = HttpStatusCode.NoContent)
    }
    val client = RepertoireCatalogClient(httpClient = HttpClient(engine), baseUrl = TEST_BASE_URL)

    client.reportInstall("london-system-white")

    requestedMethod shouldBe HttpMethod.Post
    requestedUrl shouldBe "$TEST_BASE_URL/london-system-white/installs"
  }

  @Test
  fun reportInstallSwallowsANetworkFailure() = runTest {
    // Must not throw: a caller reporting a completed install never has its own state disturbed by
    // this being best-effort telemetry.
    clientThrowing().reportInstall("london-system-white")
  }

  @Test
  fun reportInstallSwallowsAnHttpErrorStatus() = runTest {
    val client = clientRespondingWith("Internal Server Error", HttpStatusCode.InternalServerError)

    client.reportInstall("london-system-white")
  }

  private companion object {
    const val TEST_BASE_URL = "https://example.invalid/catalog"
  }
}
