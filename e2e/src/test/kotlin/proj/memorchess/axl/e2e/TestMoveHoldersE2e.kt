package proj.memorchess.axl.e2e

import proj.memorchess.axl.core.auth.AccessToken
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlin.test.Test
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import proj.memorchess.axl.core.data.DataMove
import proj.memorchess.axl.core.data.DirtyKey
import proj.memorchess.axl.core.data.InMemoryDatabaseQueryManager
import proj.memorchess.axl.core.data.PositionKey
import proj.memorchess.axl.core.engine.GameEngine
import proj.memorchess.axl.core.graph.InstalledMove
import proj.memorchess.axl.core.graph.NodeCache
import proj.memorchess.axl.core.graph.Prefetcher
import proj.memorchess.axl.core.graph.RepertoireTagStore
import proj.memorchess.axl.core.graph.TrainableProjection
import proj.memorchess.axl.core.graph.TreeStore
import proj.memorchess.axl.core.sync.DeviceIdentity
import proj.memorchess.axl.core.sync.DevicePlatform
import proj.memorchess.axl.core.sync.SyncApiClient
import proj.memorchess.axl.core.sync.SyncApplier
import proj.memorchess.axl.core.sync.SyncPullOutcome
import proj.memorchess.axl.core.sync.SyncPushOutcome
import proj.memorchess.axl.core.sync.SyncPushRequest
import proj.memorchess.axl.core.sync.SyncRegisterOutcome
import proj.memorchess.axl.core.sync.toEdgeRepertoireTagSyncRow
import proj.memorchess.axl.core.sync.toEdgeSyncRow
import proj.memorchess.axl.core.sync.toNodeSyncRow
import proj.memorchess.axl.core.sync.toRepertoireSyncRow
import proj.memorchess.axl.server.startE2eServer

/** Drives two devices' graphs through a real local `:server` to prove move holders converge. */
class TestMoveHoldersE2e {

  private val client = SyncApiClient(httpClient, "${server.baseUrl}/v1")

  /** One device: its own store and graph, wired like the app but over an in memory database. */
  private class Device {
    val identity = DeviceIdentity.ephemeral()
    val database = InMemoryDatabaseQueryManager()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val cache = NodeCache({ database.getPosition(it) }, scope)
    private val trainable = TrainableProjection(database, cache)
    private val tagStore = RepertoireTagStore(database, identity, trainable)
    val treeStore =
      TreeStore(database, cache, Prefetcher(cache, scope), trainable, tagStore, identity)
    val applier = SyncApplier(database, cache, trainable)

    suspend fun move(from: PositionKey, san: String): DataMove? =
      database.getPositionIncludingDeleted(from)?.previousAndNextMoves?.nextMoves?.get(san)
  }

  private suspend fun register(token: AccessToken, device: Device) {
    client
      .registerDevice(token, device.identity.originDevice, DevicePlatform.JVM, afterReset = false)
      .shouldBeInstanceOf<SyncRegisterOutcome.Ok>()
  }

  /** Pushes [device]'s outbox the way the app does. */
  private suspend fun push(token: AccessToken, device: Device) {
    val database = device.database
    val outbox = database.getOutbox()
    val request =
      SyncPushRequest(
        nodes =
          outbox.mapNotNull {
            (it.key as? DirtyKey.NodeKey)?.let { key ->
              database.getPositionIncludingDeleted(key.positionKey)?.toNodeSyncRow()
            }
          },
        edges =
          outbox.mapNotNull {
            (it.key as? DirtyKey.EdgeKey)?.let { key ->
              database
                .getPositionIncludingDeleted(key.origin)
                ?.previousAndNextMoves
                ?.nextMoves
                ?.values
                ?.firstOrNull { move -> move.destination == key.destination }
                ?.toEdgeSyncRow()
            }
          },
        settings = emptyList(),
        repertoires =
          outbox.mapNotNull {
            (it.key as? DirtyKey.RepertoireKey)?.let { key ->
              database.getRepertoireIncludingDeleted(key.repertoireId)?.toRepertoireSyncRow()
            }
          },
        tags =
          outbox.mapNotNull {
            (it.key as? DirtyKey.TagKey)?.let { key ->
              database
                .getTagIncludingDeleted(key.origin, key.destination, key.repertoireId)
                ?.toEdgeRepertoireTagSyncRow()
            }
          },
        device = device.identity.originDevice,
      )
    client
      .push(token, request)
      .shouldBeInstanceOf<SyncPushOutcome.Ok>()
      .response
      .rejected shouldHaveSize 0
    database.clearDirty(outbox)
  }

  /** Pulls every page for [device] and applies it in the app's order. */
  private suspend fun pull(token: AccessToken, device: Device, limit: Int = PAGE_LIMIT) {
    var ack: String? = null
    repeat(MAX_PAGES * PAGE_LIMIT) {
      val page =
        client
          .pull(token, device.identity.originDevice, ack, limit)
          .shouldBeInstanceOf<SyncPullOutcome.Ok>()
          .response
      if (
        page.nodes.isEmpty() &&
          page.edges.isEmpty() &&
          page.repertoires.isEmpty() &&
          page.tags.isEmpty() &&
          page.settings.isEmpty()
      ) {
        return
      }
      for (node in page.nodes) device.applier.applyNode(node)
      for (edge in page.edges) device.applier.applyMove(edge)
      for (repertoire in page.repertoires) device.applier.applyRepertoire(repertoire)
      for (tag in page.tags) device.applier.applyTag(tag)
      ack = page.pageToken
    }
    error("pull did not terminate")
  }

  @Test
  fun `an install reaches another device with its markings and no manual hold`() =
    runBlocking<Unit> {
      val token = AccessToken(server.tokenFor(server.newUserId()))
      val deviceA = Device()
      val deviceB = Device()
      register(token, deviceA)
      register(token, deviceB)
      val start = PositionKey.START_POSITION
      val afterE4 = GameEngine().apply { playSanMove("e4") }.toPositionKey()
      val afterE5 =
        GameEngine().apply { playSanMove("e4") }.apply { playSanMove("e5") }.toPositionKey()

      deviceA.treeStore.install(
        "london",
        listOf(
          InstalledMove(start, "e4", afterE4, isGood = true, fromDepth = 0),
          InstalledMove(afterE4, "e5", afterE5, isGood = false, fromDepth = 1),
        ),
      )
      push(token, deviceA)
      pull(token, deviceB)

      deviceB.move(start, "e4")!!.isGood shouldBe true
      deviceB.move(start, "e4")!!.ownIsGood shouldBe null
      deviceB.move(afterE4, "e5")!!.isGood shouldBe false
      deviceB.move(afterE4, "e5")!!.ownIsGood shouldBe null

      deviceB.treeStore.addMove(start, "e4", afterE4, isGood = true, fromDepth = 0)
      push(token, deviceB)
      pull(token, deviceA)

      deviceA.move(start, "e4")!!.ownIsGood shouldBe true
      deviceA.move(afterE4, "e5")!!.ownIsGood shouldBe null
    }

  @Test
  fun `an install reaches a device whose pull serves the moves before their positions`() =
    runBlocking<Unit> {
      val token = AccessToken(server.tokenFor(server.newUserId()))
      val deviceA = Device()
      val deviceB = Device()
      register(token, deviceA)
      register(token, deviceB)
      val start = PositionKey.START_POSITION
      val afterE4 = GameEngine().apply { playSanMove("e4") }.toPositionKey()
      val afterE5 =
        GameEngine().apply { playSanMove("e4") }.apply { playSanMove("e5") }.toPositionKey()
      deviceA.treeStore.install(
        "london",
        listOf(
          InstalledMove(start, "e4", afterE4, isGood = true, fromDepth = 0),
          InstalledMove(afterE4, "e5", afterE5, isGood = false, fromDepth = 1),
        ),
      )
      push(token, deviceA)
      // A review re-pushes the position at a later revision than the tags that reach it.
      val reviewed = deviceA.treeStore.node(afterE4)!!.cardState.copy(reps = 1)
      deviceA.treeStore.updateCardState(afterE4, reviewed)
      push(token, deviceA)

      pull(token, deviceB, limit = 1)

      deviceB.move(start, "e4")!!.isGood shouldBe true
      deviceB.move(afterE4, "e5")!!.isGood shouldBe false
    }

  private companion object {
    val server = startE2eServer()

    val httpClient =
      HttpClient(CIO) { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }

    const val PAGE_LIMIT = 500
    const val MAX_PAGES = 20
  }
}
