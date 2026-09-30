package proj.memorchess.axl.e2e

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import java.util.UUID
import kotlin.test.Test
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import proj.memorchess.axl.core.data.repertoire.CatalogResult
import proj.memorchess.axl.core.data.repertoire.PublishOutcome
import proj.memorchess.axl.core.data.repertoire.RepertoireCatalogClient
import proj.memorchess.axl.core.data.repertoire.RepertoireManifest
import proj.memorchess.axl.core.data.repertoire.RepertoirePublishClient
import proj.memorchess.axl.core.pgn.PgnGame
import proj.memorchess.axl.server.startE2eServer

/** Drives composeApp's catalog and publish clients against a real local `:server`. */
class TestRepertoireCatalogE2e {

  private val repertoiresUrl = "${server.baseUrl}/v1/repertoires"
  private val publishClient = RepertoirePublishClient(httpClient, repertoiresUrl)
  private val catalogClient = RepertoireCatalogClient(httpClient, repertoiresUrl)

  @Test
  fun `a published repertoire is listed in the manifest and its pgn downloads`() =
    runBlocking<Unit> {
      val author = server.newUserId()
      val id = UUID.randomUUID().toString()

      val published =
        publishClient.publish(
          accessToken = server.tokenFor(author),
          id = id,
          title = "E2E round trip",
          description = "Published by TestRepertoireCatalogE2e",
          side = "white",
          pgn = PGN,
        )
      val manifest =
        catalogClient
          .fetchManifest()
          .shouldBeInstanceOf<CatalogResult.Ok<RepertoireManifest>>()
          .value
      val listed = manifest.repertoires.single { it.id == id }
      val games =
        catalogClient
          .fetchPgn(listed.file)
          .shouldBeInstanceOf<CatalogResult.Ok<List<PgnGame>>>()
          .value

      published.shouldBeInstanceOf<PublishOutcome.Published>().descriptor.file shouldBe listed.file
      games.single().moves.map { it.san } shouldBe listOf("e4")
    }

  @Test
  fun `a publish without a valid token is refused as unauthorized`() =
    runBlocking<Unit> {
      val outcome =
        publishClient.publish(
          accessToken = "not-a-jwt",
          id = UUID.randomUUID().toString(),
          title = "E2E unauthorized",
          description = "Must never be stored",
          side = "white",
          pgn = PGN,
        )

      outcome shouldBe PublishOutcome.Unauthorized
    }

  @Test
  fun `a repertoire published through one server downloads through another`() =
    runBlocking<Unit> {
      val id = UUID.randomUUID().toString()
      publishClient
        .publish(
          accessToken = server.tokenFor(server.newUserId()),
          id = id,
          title = "E2E shared state",
          description = "Published on the first server",
          side = "white",
          pgn = PGN,
        )
        .shouldBeInstanceOf<PublishOutcome.Published>()
      val otherCatalog =
        RepertoireCatalogClient(httpClient, "${otherServer.baseUrl}/v1/repertoires")

      val listed =
        otherCatalog
          .fetchManifest()
          .shouldBeInstanceOf<CatalogResult.Ok<RepertoireManifest>>()
          .value
          .repertoires
          .single { it.id == id }
      val result = otherCatalog.fetchPgn(listed.file)

      result.shouldBeInstanceOf<CatalogResult.Ok<List<PgnGame>>>()
    }

  @Test
  fun `a pgn that was never published is a 404`() =
    runBlocking<Unit> {
      val result = catalogClient.fetchPgn("pgn/${"0".repeat(64)}.pgn")

      result shouldBe CatalogResult.HttpError(404)
    }

  private companion object {
    val server = startE2eServer()

    /** A second server in the same JVM, as a second end to end test class would start. */
    val otherServer = startE2eServer()

    val httpClient =
      HttpClient(CIO) { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }

    const val PGN = "[Event \"e2e\"]\n[Result \"*\"]\n\n1. e4 *"
  }
}
