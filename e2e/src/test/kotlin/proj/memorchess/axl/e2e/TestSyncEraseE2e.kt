package proj.memorchess.axl.e2e

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import java.util.UUID
import kotlin.test.Test
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import proj.memorchess.axl.core.sync.DevicePlatform
import proj.memorchess.axl.core.sync.EdgeRepertoireTagSyncRow
import proj.memorchess.axl.core.sync.EdgeSyncRow
import proj.memorchess.axl.core.sync.NodeSyncRow
import proj.memorchess.axl.core.sync.RepertoireSyncRow
import proj.memorchess.axl.core.sync.ResolutionSource
import proj.memorchess.axl.core.sync.SyncApiClient
import proj.memorchess.axl.core.sync.SyncEraseOutcome
import proj.memorchess.axl.core.sync.SyncPullOutcome
import proj.memorchess.axl.core.sync.SyncPushOutcome
import proj.memorchess.axl.core.sync.SyncPushRequest
import proj.memorchess.axl.core.sync.SyncRegisterOutcome
import proj.memorchess.axl.core.sync.resolve
import proj.memorchess.axl.server.startE2eServer

/** Drives composeApp's [SyncApiClient] against a real local `:server` to prove erase converges. */
class TestSyncEraseE2e {

  private val client = SyncApiClient(httpClient, "${server.baseUrl}/v1")

  /** Everything one device was served while catching up. */
  private class Caught(
    val nodes: List<NodeSyncRow>,
    val edges: List<EdgeSyncRow>,
    val repertoires: List<RepertoireSyncRow>,
    val tags: List<EdgeRepertoireTagSyncRow>,
  )

  private suspend fun register(token: String, device: String) {
    client
      .registerDevice(token, device, DevicePlatform.JVM, afterReset = false)
      .shouldBeInstanceOf<SyncRegisterOutcome.Ok>()
  }

  /** Pulls page after page, confirming each with the next request, until one comes back empty. */
  private suspend fun pullAll(token: String, device: String): Caught {
    val nodes = mutableListOf<NodeSyncRow>()
    val edges = mutableListOf<EdgeSyncRow>()
    val repertoires = mutableListOf<RepertoireSyncRow>()
    val tags = mutableListOf<EdgeRepertoireTagSyncRow>()
    var ack: String? = null
    repeat(MAX_PAGES) {
      val page =
        client
          .pull(token, device, ack, PAGE_LIMIT)
          .shouldBeInstanceOf<SyncPullOutcome.Ok>()
          .response
      if (
        page.nodes.isEmpty() &&
          page.edges.isEmpty() &&
          page.repertoires.isEmpty() &&
          page.tags.isEmpty() &&
          page.settings.isEmpty()
      ) {
        return Caught(nodes, edges, repertoires, tags)
      }
      nodes += page.nodes
      edges += page.edges
      repertoires += page.repertoires
      tags += page.tags
      ack = page.pageToken
    }
    error("pull did not terminate")
  }

  private fun node(key: String, updatedAt: Instant, device: String) =
    NodeSyncRow(
      positionKey = key,
      dueDate = updatedAt,
      lastReview = null,
      firstReview = null,
      stability = 1.0,
      difficulty = 1.0,
      reps = 0,
      lapses = 0,
      phase = "NEW",
      step = 0,
      isDeleted = false,
      updatedAt = updatedAt,
      originDevice = device,
      deviceSeq = 1,
    )

  private fun edge(origin: String, destination: String, updatedAt: Instant, device: String) =
    EdgeSyncRow(
      origin = origin,
      destination = destination,
      move = "e4",
      isGood = true,
      isDeleted = false,
      updatedAt = updatedAt,
      originDevice = device,
      deviceSeq = 1,
    )

  private fun repertoire(id: String, updatedAt: Instant, device: String) =
    RepertoireSyncRow(
      id = id,
      name = "E2E erase",
      color = "WHITE",
      isDeleted = false,
      updatedAt = updatedAt,
      originDevice = device,
      deviceSeq = 1,
    )

  private fun tag(
    origin: String,
    destination: String,
    repertoireId: String,
    updatedAt: Instant,
    device: String,
  ) =
    EdgeRepertoireTagSyncRow(
      origin = origin,
      destination = destination,
      repertoireId = repertoireId,
      move = "e4",
      isDeleted = false,
      updatedAt = updatedAt,
      originDevice = device,
      deviceSeq = 1,
    )

  private fun fen(suffix: String) = "e2e-${UUID.randomUUID()}-$suffix"

  private suspend fun erase(token: String, device: String): Instant =
    client
      .erase(token, device, deviceSeq = 1)
      .shouldBeInstanceOf<SyncEraseOutcome.Ok>()
      .response
      .serverTime

  @Test
  fun `an erase from one device reaches the other`() =
    runBlocking<Unit> {
      val token = server.tokenFor(server.newUserId())
      val deviceA = UUID.randomUUID().toString()
      val deviceB = UUID.randomUUID().toString()
      register(token, deviceA)
      register(token, deviceB)
      val now = Clock.System.now()
      val from = fen("from")
      val to = fen("to")
      val repertoireId = "e2e-${UUID.randomUUID()}"
      client
        .push(
          token,
          SyncPushRequest(
            nodes = listOf(node(from, now, deviceB)),
            edges = listOf(edge(from, to, now, deviceB)),
            settings = emptyList(),
            repertoires = listOf(repertoire(repertoireId, now, deviceB)),
            tags = listOf(tag(from, to, repertoireId, now, deviceB)),
            device = deviceB,
          ),
        )
        .shouldBeInstanceOf<SyncPushOutcome.Ok>()
        .response
        .rejected shouldHaveSize 0

      erase(token, deviceA)
      val caught = pullAll(token, deviceB)

      caught.nodes.map { it.isDeleted } shouldBe listOf(true)
      caught.edges.map { it.isDeleted } shouldBe listOf(true)
      caught.repertoires.map { it.isDeleted } shouldBe listOf(true)
      caught.tags.map { it.isDeleted } shouldBe listOf(true)
    }

  @Test
  fun `an unsynced import from before the erase does not survive`() =
    runBlocking<Unit> {
      val token = server.tokenFor(server.newUserId())
      val deviceA = UUID.randomUUID().toString()
      val deviceB = UUID.randomUUID().toString()
      register(token, deviceA)
      register(token, deviceB)
      val erasedAt = erase(token, deviceA)
      val staleAt = Clock.System.now() - 1.minutes
      (staleAt < erasedAt) shouldBe true
      val imported = node(fen("imported"), staleAt, deviceB)
      client
        .push(
          token,
          SyncPushRequest(
            nodes = listOf(imported),
            edges = emptyList(),
            settings = emptyList(),
            device = deviceB,
          ),
        )
        .shouldBeInstanceOf<SyncPushOutcome.Ok>()
        .response
        .rejected shouldHaveSize 0

      val pulledByB = pullAll(token, deviceB).nodes.single()
      val pulledByA = pullAll(token, deviceA).nodes.single()

      pulledByB.isDeleted shouldBe true
      resolve(local = imported, remote = pulledByB).source shouldBe ResolutionSource.REMOTE
      pulledByA.positionKey shouldBe imported.positionKey
      pulledByA.isDeleted shouldBe true
    }

  private companion object {
    val server = startE2eServer()

    val httpClient =
      HttpClient(CIO) { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }

    const val PAGE_LIMIT = 500
    const val MAX_PAGES = 20
  }
}
