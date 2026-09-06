package proj.memorchess.axl.core.data.repertoire

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlinx.coroutines.test.runTest
import proj.memorchess.axl.core.pgn.PgnGame

class TestRepertoireCatalogClient {

  private val manifestJson =
    """
    {
      "schemaVersion": 1,
      "repertoires": [
        {
          "id": "london-system-white",
          "name": "London System",
          "color": "white",
          "description": "A solid system for White.",
          "moveCount": 73,
          "file": "pgn/london-system-white.pgn",
          "futureField": "ignored"
        },
        {
          "id": "caro-kann-black",
          "name": "Caro-Kann",
          "color": "black",
          "description": "A rock solid defense for Black.",
          "moveCount": 74,
          "file": "pgn/caro-kann-black.pgn"
        }
      ],
      "futureTopLevelField": {"nested": true}
    }
    """
      .trimIndent()

  private val validPgn =
    """
    [Event "MemorChess Repertoire"]
    [Result "*"]

    1. e4 e5 (1... c5 2. Nf3 d6) 2. Nf3 Nc6 3. Bb5 *
    """
      .trimIndent()

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
    manifest.schemaVersion shouldBe 1
    manifest.repertoires shouldHaveSize 2
    manifest.repertoires[0].id shouldBe "london-system-white"
    manifest.repertoires[0].color shouldBe RepertoireColor.WHITE
    manifest.repertoires[0].moveCount shouldBe 73
    manifest.repertoires[1].color shouldBe RepertoireColor.BLACK
    manifest.repertoires[1].file shouldBe "pgn/caro-kann-black.pgn"
  }

  @Test
  fun fetchManifestAcceptsZeroAndLargeMoveCounts() = runTest {
    val edgeManifest =
      """
      {
        "schemaVersion": 1,
        "repertoires": [
          {"id": "empty", "name": "Empty", "color": "white", "description": "",
           "moveCount": 0, "file": "pgn/empty.pgn"},
          {"id": "huge", "name": "Huge", "color": "black", "description": "",
           "moveCount": 2147483647, "file": "pgn/huge.pgn"}
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
        "schemaVersion": 1,
        "repertoires": [
          {"id": "x", "name": "X", "color": "purple", "description": "",
           "moveCount": 1, "file": "pgn/x.pgn"}
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
    val client = clientRespondingWith("{\"schemaVersion\": 2, \"repertoires\": []}")

    val result = client.fetchManifest()

    result.shouldBeInstanceOf<CatalogResult.MalformedManifest>()
  }

  @Test
  fun fetchPgnParsesValidDocument() = runTest {
    val client = clientRespondingWith(validPgn)

    val result = client.fetchPgn("pgn/test.pgn")

    result.shouldBeInstanceOf<CatalogResult.Ok<List<PgnGame>>>()
    val games = result.value
    games shouldHaveSize 1
    games[0].moves[0].san shouldBe "e4"
  }

  @Test
  fun fetchPgnNotFoundReturnsHttpError() = runTest {
    val client = clientRespondingWith("Not Found", HttpStatusCode.NotFound)

    val result = client.fetchPgn("pgn/missing.pgn")

    result shouldBe CatalogResult.HttpError(404)
  }

  @Test
  fun fetchPgnNetworkFailureReturnsNetworkError() = runTest {
    val client = clientThrowing()

    val result = client.fetchPgn("pgn/test.pgn")

    result.shouldBeInstanceOf<CatalogResult.NetworkError>()
  }

  @Test
  fun fetchPgnMalformedDocumentReturnsMalformedPgn() = runTest {
    val client = clientRespondingWith("1. e4 (1... e5")

    val result = client.fetchPgn("pgn/broken.pgn")

    result.shouldBeInstanceOf<CatalogResult.MalformedPgn>()
  }

  @Test
  fun fetchPgnWithoutAnyMoveReturnsMalformedPgn() = runTest {
    val client = clientRespondingWith("[Event \"Empty\"]\n[Result \"*\"]\n\n*")

    val result = client.fetchPgn("pgn/empty.pgn")

    result.shouldBeInstanceOf<CatalogResult.MalformedPgn>()
  }

  @Test
  fun fetchPgnReportsDownloadProgressEndingAtOne() = runTest {
    val engine = MockEngine { _ ->
      respond(
        content = validPgn,
        status = HttpStatusCode.OK,
        headers =
          headersOf(
            "Content-Type" to listOf("text/plain; charset=utf-8"),
            "Content-Length" to listOf(validPgn.encodeToByteArray().size.toString()),
          ),
      )
    }
    val client = RepertoireCatalogClient(httpClient = HttpClient(engine), baseUrl = TEST_BASE_URL)
    val reported = mutableListOf<Float>()

    val result = client.fetchPgn("pgn/test.pgn", onProgress = reported::add)

    result.shouldBeInstanceOf<CatalogResult.Ok<List<PgnGame>>>()
    reported shouldHaveSize 1
    reported.last() shouldBe 1f
  }

  @Test
  fun fetchPgnWithoutAProgressCallbackStillParses() = runTest {
    val client = clientRespondingWith(validPgn)

    val result = client.fetchPgn("pgn/test.pgn")

    result.shouldBeInstanceOf<CatalogResult.Ok<List<PgnGame>>>()
  }

  @Test
  fun fetchPgnWithoutContentLengthReportsNoProgress() = runTest {
    val client = clientRespondingWith(validPgn)
    val reported = mutableListOf<Float>()

    val result = client.fetchPgn("pgn/test.pgn", onProgress = reported::add)

    result.shouldBeInstanceOf<CatalogResult.Ok<List<PgnGame>>>()
    reported shouldBe emptyList()
  }

  @Test
  fun fetchPgnWithZeroContentLengthReportsNoProgress() = runTest {
    // An honestly empty body, Content-Length included, is the boundary case that would otherwise
    // divide by zero when computing bytesSentTotal / contentLength.
    val engine = MockEngine { _ ->
      respond(
        content = "",
        status = HttpStatusCode.OK,
        headers =
          headersOf(
            "Content-Type" to listOf("text/plain; charset=utf-8"),
            "Content-Length" to listOf("0"),
          ),
      )
    }
    val client = RepertoireCatalogClient(httpClient = HttpClient(engine), baseUrl = TEST_BASE_URL)
    val reported = mutableListOf<Float>()

    client.fetchPgn("pgn/test.pgn", onProgress = reported::add)

    // Nothing to divide by: guarded rather than reporting a NaN or infinite fraction.
    reported shouldBe emptyList()
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
