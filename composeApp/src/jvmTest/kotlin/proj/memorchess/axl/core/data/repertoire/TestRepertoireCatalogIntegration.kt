package proj.memorchess.axl.core.data.repertoire

import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.client.HttpClient
import kotlin.test.Test
import kotlinx.coroutines.test.runTest
import proj.memorchess.axl.core.pgn.PgnGame

/**
 * Live integration test that downloads the real catalog from a deployed `:server`.
 *
 * The catalog root comes from the `MEMORCHESS_CATALOG_URL` environment variable, set only by the
 * scheduled `catalog-canary` workflow. Without it the test reports a passing no op, so pull request
 * CI and local runs never depend on a deployment being reachable. When it is set the assertions are
 * deliberately hard, including on a 5xx. This is the end to end guarantee that the published
 * manifest and every published PGN file stay compatible with the Kotlin PGN parser, and tolerating
 * an error status would hide a real server regression. The catalog is populated by users publishing
 * their own repertoires, so an empty manifest is a valid outcome and not itself a failure.
 */
class TestRepertoireCatalogIntegration {

  /** Downloads the real manifest and validates every listed PGN file with the PGN parser. */
  @Test
  fun fetchRealManifestAndParseEveryListedPgn() = runTest {
    val baseUrl =
      System.getenv("MEMORCHESS_CATALOG_URL")?.trim()?.takeIf { it.isNotBlank() }
        ?: run {
          println("Skipping live catalog test: MEMORCHESS_CATALOG_URL not set")
          return@runTest
        }
    val client = RepertoireCatalogClient(httpClient = HttpClient(), baseUrl = baseUrl)

    val manifestResult = client.fetchManifest()

    val manifest =
      withClue("Fetching the catalog manifest from $baseUrl failed: $manifestResult.") {
        manifestResult.shouldBeInstanceOf<CatalogResult.Ok<RepertoireManifest>>().value
      }
    for (descriptor in manifest.repertoires) {
      val pgnResult = client.fetchPgn(descriptor.file)
      val games =
        withClue(
          "Downloading or parsing ${descriptor.file} (${descriptor.id}) failed: $pgnResult"
        ) {
          pgnResult.shouldBeInstanceOf<CatalogResult.Ok<List<PgnGame>>>().value
        }
      withClue("Repertoire ${descriptor.id} parsed to a document without moves") {
        games.flatMap { it.moves }.shouldNotBeEmpty()
      }
    }
  }
}
