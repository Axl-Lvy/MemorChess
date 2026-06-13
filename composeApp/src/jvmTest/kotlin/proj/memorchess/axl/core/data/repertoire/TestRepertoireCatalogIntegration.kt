package proj.memorchess.axl.core.data.repertoire

import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.client.HttpClient
import kotlin.test.Test
import kotlinx.coroutines.test.runTest
import proj.memorchess.axl.core.pgn.PgnGame

/**
 * Live integration test that downloads the real catalog from the `repertoire-data` branch on raw
 * GitHub.
 *
 * Unlike the Lichess integration test this one needs no secret, so it always runs. It is the end to
 * end guarantee that the published manifest and every published PGN file stay compatible with the
 * Kotlin PGN parser. A failure here with a network related clue means the machine is offline or
 * GitHub is unreachable, not that the code is broken.
 */
class TestRepertoireCatalogIntegration {

  /** Downloads the real manifest and validates every listed PGN file with the PGN parser. */
  @Test
  fun fetchRealManifestAndParseEveryListedPgn() = runTest {
    val client = RepertoireCatalogClient(httpClient = HttpClient())

    val manifestResult = client.fetchManifest()

    val manifest =
      withClue(
        "Fetching the live catalog manifest failed: $manifestResult." +
          " This test needs network access to raw.githubusercontent.com."
      ) {
        manifestResult.shouldBeInstanceOf<CatalogResult.Ok<RepertoireManifest>>().value
      }
    manifest.repertoires.shouldNotBeEmpty()
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
