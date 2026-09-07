package proj.memorchess.axl.core.data.repertoire

import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.client.HttpClient
import kotlin.test.Test
import kotlinx.coroutines.test.runTest
import proj.memorchess.axl.core.pgn.PgnGame

/**
 * Live integration test that downloads the real catalog from the production `:server` deployment.
 *
 * Unlike the Lichess integration test this one needs no secret, so it always runs. It is the end to
 * end guarantee that the published manifest and every published PGN file stay compatible with the
 * Kotlin PGN parser. A failure here with a network related clue means the machine is offline or the
 * server is unreachable, not that the code is broken. The catalog is populated by users publishing
 * their own repertoires, so an empty manifest is a valid outcome and not itself a failure.
 */
class TestRepertoireCatalogIntegration {

  /** Downloads the real manifest and validates every listed PGN file with the PGN parser. */
  @Test
  fun fetchRealManifestAndParseEveryListedPgn() = runTest {
    val client =
      RepertoireCatalogClient(
        httpClient = HttpClient(),
        baseUrl = "https://memorchess.axl-lvy.fr/v1/repertoires",
      )

    val manifestResult = client.fetchManifest()

    val manifest =
      withClue(
        "Fetching the live catalog manifest failed: $manifestResult." +
          " This test needs network access to memorchess.axl-lvy.fr."
      ) {
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
