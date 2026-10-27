package proj.memorchess.axl.core.data.repertoire

import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.client.HttpClient
import kotlin.test.Test
import kotlinx.coroutines.test.runTest
import org.junit.Assume.assumeTrue

/** Environment variable holding the root URL of the catalog to check, without a trailing slash. */
private const val CATALOG_URL_ENV = "MEMORCHESS_CATALOG_URL"

/**
 * Live canary over a deployed `:server`'s catalog, skipped unless [CATALOG_URL_ENV] is set. Users
 * populate the catalog, so an empty manifest is valid.
 */
class TestRepertoireCatalogIntegration {

  @Test
  fun fetchRealManifestAndEveryListedRepertoire() {
    val baseUrl = System.getenv(CATALOG_URL_ENV)?.trim().orEmpty()
    assumeTrue("$CATALOG_URL_ENV not set, skipping the live catalog canary", baseUrl.isNotEmpty())

    runTest {
      val client = RepertoireCatalogClient(httpClient = HttpClient(), baseUrl = baseUrl)

      val manifestResult = client.fetchManifest()

      val manifest =
        withClue("Fetching the live catalog manifest from $baseUrl failed: $manifestResult") {
          manifestResult.shouldBeInstanceOf<CatalogResult.Ok<RepertoireManifest>>().value
        }
      for (descriptor in manifest.repertoires) {
        val movesResult = client.fetchMoves(descriptor.id, descriptor.version)
        val moves =
          withClue(
            "Downloading ${descriptor.id} version ${descriptor.version} failed: $movesResult"
          ) {
            movesResult.shouldBeInstanceOf<CatalogResult.Ok<List<RepertoireMove>>>().value
          }
        withClue("Repertoire ${descriptor.id} has no moves") { moves.shouldNotBeEmpty() }
      }
    }
  }
}
