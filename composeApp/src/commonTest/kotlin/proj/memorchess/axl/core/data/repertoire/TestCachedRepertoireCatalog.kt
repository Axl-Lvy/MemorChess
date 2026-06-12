package proj.memorchess.axl.core.data.repertoire

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import proj.memorchess.axl.core.config.REPERTOIRE_MANIFEST_CACHE_SETTING
import proj.memorchess.axl.core.config.REPERTOIRE_MANIFEST_FETCHED_AT_SETTING
import proj.memorchess.axl.core.date.DateUtil
import proj.memorchess.axl.test_util.TestWithKoin

class TestCachedRepertoireCatalog : TestWithKoin() {

  override suspend fun setUp() {
    REPERTOIRE_MANIFEST_CACHE_SETTING.reset()
    REPERTOIRE_MANIFEST_FETCHED_AT_SETTING.reset()
  }

  private val manifestJson =
    """
    {
      "schemaVersion": 1,
      "repertoires": [
        {"id": "london-system-white", "name": "London System", "color": "white",
         "description": "Solid.", "moveCount": 73, "file": "pgn/london-system-white.pgn"}
      ]
    }
    """
      .trimIndent()

  private fun countingClient(
    status: HttpStatusCode = HttpStatusCode.OK,
    onCall: () -> Unit = {},
  ): RepertoireCatalogClient {
    val engine = MockEngine { _ ->
      onCall()
      respond(
        content = manifestJson,
        status = status,
        headers = headersOf("Content-Type", "text/plain; charset=utf-8"),
      )
    }
    return RepertoireCatalogClient(
      httpClient = HttpClient(engine),
      baseUrl = "https://example.invalid/catalog",
    )
  }

  private fun seedCache(json: String, fetchedAt: Instant) {
    REPERTOIRE_MANIFEST_CACHE_SETTING.setValue(json)
    REPERTOIRE_MANIFEST_FETCHED_AT_SETTING.setValue(fetchedAt)
  }

  @Test
  fun freshCacheHitSkipsNetwork() = test {
    seedCache(manifestJson, DateUtil.now())
    var networkCalls = 0
    val catalog = CachedRepertoireCatalog(countingClient(onCall = { networkCalls++ }), ttl = 1.days)

    val result = catalog.getManifest()

    result.shouldBeInstanceOf<CachedManifestResult.Fresh>()
    result.manifest.repertoires.single().id shouldBe "london-system-white"
    networkCalls shouldBe 0
  }

  @Test
  fun staleCacheIsRefreshedFromNetwork() = test {
    seedCache(manifestJson, Instant.parse("2020-01-01T00:00:00Z"))
    var networkCalls = 0
    val catalog = CachedRepertoireCatalog(countingClient(onCall = { networkCalls++ }), ttl = 1.days)

    val result = catalog.getManifest()

    result.shouldBeInstanceOf<CachedManifestResult.Fresh>()
    networkCalls shouldBe 1
  }

  @Test
  fun staleCacheIsReturnedWhenNetworkFails() = test {
    seedCache(manifestJson, Instant.parse("2020-01-01T00:00:00Z"))
    val catalog =
      CachedRepertoireCatalog(countingClient(status = HttpStatusCode.ServiceUnavailable))

    val result = catalog.getManifest()

    result.shouldBeInstanceOf<CachedManifestResult.Stale>()
    result.manifest.repertoires.single().moveCount shouldBe 73
  }

  @Test
  fun successfulFetchPopulatesCache() = test {
    val catalog = CachedRepertoireCatalog(countingClient())

    val result = catalog.getManifest()

    result.shouldBeInstanceOf<CachedManifestResult.Fresh>()
    REPERTOIRE_MANIFEST_CACHE_SETTING.getValue() shouldContain "london-system-white"
    val fetchedAt = REPERTOIRE_MANIFEST_FETCHED_AT_SETTING.getValue()
    (DateUtil.now() - fetchedAt < 1.days) shouldBe true
  }

  @Test
  fun httpErrorWithoutCacheReturnsHttpError() = test {
    val catalog = CachedRepertoireCatalog(countingClient(status = HttpStatusCode.NotFound))

    val result = catalog.getManifest()

    result shouldBe CachedManifestResult.HttpError(404)
  }

  @Test
  fun networkFailureWithoutCacheReturnsNetworkError() = test {
    val engine = MockEngine { _ -> throw RuntimeException("connection refused") }
    val catalog =
      CachedRepertoireCatalog(
        RepertoireCatalogClient(
          httpClient = HttpClient(engine),
          baseUrl = "https://example.invalid/catalog",
        )
      )

    val result = catalog.getManifest()

    result.shouldBeInstanceOf<CachedManifestResult.NetworkError>()
  }

  @Test
  fun corruptedCacheIsIgnoredAndRefreshedFromNetwork() = test {
    seedCache("not json at all", DateUtil.now())
    var networkCalls = 0
    val catalog = CachedRepertoireCatalog(countingClient(onCall = { networkCalls++ }))

    val result = catalog.getManifest()

    result.shouldBeInstanceOf<CachedManifestResult.Fresh>()
    networkCalls shouldBe 1
  }
}
