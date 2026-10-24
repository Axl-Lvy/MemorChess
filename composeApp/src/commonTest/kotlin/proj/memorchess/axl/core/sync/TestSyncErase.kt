package proj.memorchess.axl.core.sync

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.ByteReadChannel
import kotlin.test.Test
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import proj.memorchess.axl.core.auth.Account
import proj.memorchess.axl.core.auth.AuthProvider
import proj.memorchess.axl.core.auth.SignInResult
import proj.memorchess.axl.core.auth.TokenResult
import proj.memorchess.axl.core.data.InMemoryDatabaseQueryManager
import proj.memorchess.axl.core.data.PositionKey
import proj.memorchess.axl.core.data.repertoire.RepertoireColor
import proj.memorchess.axl.test_util.testRepertoireTagStore
import proj.memorchess.axl.test_util.testSyncApplier
import proj.memorchess.axl.test_util.testTreeStore

/** Always returns the same [TokenResult]. */
private class FixedTokenAuthProvider(private val result: TokenResult) : AuthProvider {
  override val currentAccount: StateFlow<Account?> = MutableStateFlow(null)

  override suspend fun signIn(): SignInResult = error("not used")

  override fun signOut() = error("not used")

  override suspend fun accessToken(): TokenResult = result
}

class TestSyncErase {

  private val origin = PositionKey.START_POSITION
  private val destination = PositionKey("posA b K")
  private val repertoireId = "italian-game"

  private fun jsonClient(engine: MockEngine): HttpClient =
    HttpClient(engine) { install(ContentNegotiation) { json(SYNC_JSON) } }

  private fun MockRequestHandleScope.jsonResponse(
    body: String,
    status: HttpStatusCode = HttpStatusCode.OK,
  ) =
    respond(
      content = ByteReadChannel(body),
      status = status,
      headers = headersOf(HttpHeaders.ContentType, "application/json"),
    )

  private fun MockRequestHandleScope.noContent() =
    respond(content = ByteReadChannel(""), status = HttpStatusCode.NoContent)

  private fun HttpRequestData.isErase() =
    method.value == "POST" && url.encodedPath == "/v1/me/erase"

  private fun HttpRequestData.textBody() = (body as TextContent).text

  /** Seeds one move, one repertoire and one tag, each leaving an outbox entry behind. */
  private suspend fun seed(
    database: InMemoryDatabaseQueryManager,
    identity: DeviceIdentity = DeviceIdentity.ephemeral(),
  ) {
    val scope = CoroutineScope(Dispatchers.Unconfined)
    val tagStore = testRepertoireTagStore(database, scope, identity)
    tagStore.register(repertoireId, "Italian Game", RepertoireColor.WHITE)
    testTreeStore(database, scope, identity)
      .addMove(from = origin, move = "e4", to = destination, isGood = true, fromDepth = 0)
    tagStore.tag(origin, destination, repertoireId)
  }

  private suspend fun erase(
    database: InMemoryDatabaseQueryManager,
    token: TokenResult,
    engine: MockEngine,
    identity: DeviceIdentity = DeviceIdentity.ephemeral(),
  ): EraseOutcome =
    runErase(
      authProvider = FixedTokenAuthProvider(token),
      applier = testSyncApplier(database, CoroutineScope(Dispatchers.Unconfined)),
      apiClient = SyncApiClient(jsonClient(engine), baseUrl = "https://issuer.example/v1"),
      deviceIdentity = identity,
    )

  private suspend fun InMemoryDatabaseQueryManager.shouldBeWiped() {
    getNodesPage(null, 100).nodes.shouldBeEmpty()
    getRepertoires().shouldBeEmpty()
    getTags(origin, destination).shouldBeEmpty()
    getOutbox().shouldBeEmpty()
  }

  private suspend fun InMemoryDatabaseQueryManager.shouldBeIntact() {
    getNodesPage(null, 100).nodes.shouldNotBeEmpty()
    getRepertoires().map { it.id } shouldBe listOf(repertoireId)
    getTags(origin, destination).map { it.repertoireId } shouldBe listOf(repertoireId)
  }

  @Test
  fun signedOutErasesLocallyWithoutCallingTheServer() = runTest {
    val database = InMemoryDatabaseQueryManager()
    seed(database)
    var requests = 0
    val engine = MockEngine { _ ->
      requests++
      noContent()
    }

    val outcome = erase(database, TokenResult.SignedOut, engine)

    outcome shouldBe EraseOutcome.Erased
    requests shouldBe 0
    database.shouldBeWiped()
  }

  @Test
  fun signedInErasesOnTheServerThenLocally() = runTest {
    val database = InMemoryDatabaseQueryManager()
    seed(database)
    var eraseRequests = 0
    val engine = MockEngine { request ->
      if (request.isErase()) {
        eraseRequests++
        jsonResponse(ERASE_OK_BODY)
      } else {
        noContent()
      }
    }

    val outcome = erase(database, TokenResult.Ok("tok"), engine)

    outcome shouldBe EraseOutcome.Erased
    eraseRequests shouldBe 1
    database.shouldBeWiped()
  }

  @Test
  fun aFailedServerEraseDeletesNothing() = runTest {
    val database = InMemoryDatabaseQueryManager()
    seed(database)
    val outboxBefore = database.getOutbox()
    val engine = MockEngine { request ->
      if (request.isErase()) {
        jsonResponse("""{"code":"internal","message":"nope"}""", HttpStatusCode.InternalServerError)
      } else {
        noContent()
      }
    }

    val outcome = erase(database, TokenResult.Ok("tok"), engine)

    outcome shouldBe EraseOutcome.Failed
    database.shouldBeIntact()
    database.getOutbox() shouldBe outboxBefore
  }

  @Test
  fun aTerminalTokenFailureAsksToSignInAgain() = runTest {
    val database = InMemoryDatabaseQueryManager()
    seed(database)
    val outboxBefore = database.getOutbox()
    var requests = 0
    val engine = MockEngine { _ ->
      requests++
      noContent()
    }

    val outcome = erase(database, TokenResult.Failed.Terminal, engine)

    outcome shouldBe EraseOutcome.SignInRequired
    requests shouldBe 0
    database.shouldBeIntact()
    database.getOutbox() shouldBe outboxBefore
  }

  @Test
  fun aTransientTokenFailureFails() = runTest {
    val database = InMemoryDatabaseQueryManager()
    seed(database)
    val outboxBefore = database.getOutbox()
    var requests = 0
    val engine = MockEngine { _ ->
      requests++
      noContent()
    }

    val outcome = erase(database, TokenResult.Failed.Transient, engine)

    outcome shouldBe EraseOutcome.Failed
    requests shouldBe 0
    database.shouldBeIntact()
    database.getOutbox() shouldBe outboxBefore
  }

  @Test
  fun aResyncRequiredRegisterIsRetriedAfterReset() = runTest {
    val database = InMemoryDatabaseQueryManager()
    seed(database)
    val resetFlags = mutableListOf<Boolean>()
    var eraseRequests = 0
    val engine = MockEngine { request ->
      if (request.isErase()) {
        eraseRequests++
        jsonResponse(ERASE_OK_BODY)
      } else {
        val reported = request.textBody().contains(""""afterReset":true""")
        resetFlags += reported
        if (reported) {
          noContent()
        } else {
          jsonResponse(
            """{"code":"resync_required","message":"start over"}""",
            HttpStatusCode.Gone,
          )
        }
      }
    }

    val outcome = erase(database, TokenResult.Ok("tok"), engine)

    outcome shouldBe EraseOutcome.Erased
    resetFlags shouldBe listOf(false, true)
    eraseRequests shouldBe 1
    database.shouldBeWiped()
  }

  @Test
  fun eraseSendsAFreshDeviceSeq() = runTest {
    val database = InMemoryDatabaseQueryManager()
    val identity = DeviceIdentity.ephemeral()
    seed(database, identity)
    val stampedSeqs =
      database.getOutbox().map { it.deviceSeq } +
        database.getNodesPage(null, 100).nodes.map { it.deviceSeq }
    var sent: SyncEraseRequest? = null
    val engine = MockEngine { request ->
      if (request.isErase()) {
        sent = SYNC_JSON.decodeFromString<SyncEraseRequest>(request.textBody())
        jsonResponse(ERASE_OK_BODY)
      } else {
        noContent()
      }
    }

    erase(database, TokenResult.Ok("tok"), engine, identity)

    stampedSeqs.shouldNotBeEmpty()
    val request = sent.shouldNotBeNull()
    request.device shouldBe identity.originDevice
    request.deviceSeq shouldBeGreaterThan stampedSeqs.max()
  }

  private companion object {
    const val ERASE_OK_BODY = """{"serverTime":"2026-01-01T00:00:00Z","revision":9}"""
  }
}
