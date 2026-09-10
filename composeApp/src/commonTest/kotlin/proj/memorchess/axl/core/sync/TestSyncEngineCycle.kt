package proj.memorchess.axl.core.sync

import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
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
import proj.memorchess.axl.core.graph.TreeStore

/** Minimal fake: always returns the same [TokenResult]. */
private class FakeAuthProvider(private val result: TokenResult) : AuthProvider {
  override val currentAccount: StateFlow<Account?> = MutableStateFlow(null)

  override suspend fun signIn(): SignInResult = error("not used")

  override fun signOut() = error("not used")

  override suspend fun accessToken(): TokenResult = result
}

class TestSyncEngineCycle {

  private fun jsonClient(engine: MockEngine): HttpClient =
    HttpClient(engine) { install(ContentNegotiation) { json(SYNC_JSON) } }

  private val emptyPullBody =
    """{"serverTime":"2026-01-01T00:00:00Z","nextCursor":null,"pageToken":"tok-1","nodes":[],"edges":[],"settings":[]}"""
  private val emptyPushBody = """{"serverTime":"2026-01-01T00:00:00Z","revision":1,"rejected":[]}"""

  private fun MockRequestHandleScope.jsonResponse(body: String) =
    respond(
      content = ByteReadChannel(body),
      status = HttpStatusCode.OK,
      headers = headersOf(HttpHeaders.ContentType, "application/json"),
    )

  private fun emptyPullEngine() = MockEngine { request ->
    when (request.method.value) {
      "PUT" -> respond(content = ByteReadChannel(""), status = HttpStatusCode.NoContent)
      "GET" -> jsonResponse(emptyPullBody)
      else -> jsonResponse(emptyPushBody)
    }
  }

  private fun treeStore(database: InMemoryDatabaseQueryManager = InMemoryDatabaseQueryManager()) =
    TreeStore(database, CoroutineScope(Dispatchers.Unconfined), DeviceIdentity.ephemeral())

  @Test
  fun emptyCycleSucceedsAndLeavesCursorUntouched() = runTest {
    val database = InMemoryDatabaseQueryManager()

    val outcome =
      runSyncCycle(
        authProvider = FakeAuthProvider(TokenResult.Ok("tok")),
        database = database,
        treeStore = treeStore(database),
        apiClient =
          SyncApiClient(jsonClient(emptyPullEngine()), baseUrl = "https://issuer.example/v1"),
        deviceIdentity = DeviceIdentity.ephemeral(),
      )

    outcome shouldBe CycleOutcome.Success
  }

  @Test
  fun transientTokenFailureSkipsTheApiClientEntirely() = runTest {
    var calls = 0
    val engine = MockEngine { _ ->
      calls++
      respond(content = "", status = HttpStatusCode.InternalServerError)
    }
    val database = InMemoryDatabaseQueryManager()

    val outcome =
      runSyncCycle(
        authProvider = FakeAuthProvider(TokenResult.Failed.Transient),
        database = database,
        treeStore = treeStore(database),
        apiClient = SyncApiClient(jsonClient(engine), baseUrl = "https://issuer.example/v1"),
        deviceIdentity = DeviceIdentity.ephemeral(),
      )

    outcome shouldBe CycleOutcome.Transient
    calls shouldBe 0
  }

  @Test
  fun terminalTokenFailureAndSignedOutBothPauseTheEngine() = runTest {
    val database = InMemoryDatabaseQueryManager()
    val engine = MockEngine { _ -> error("should not call HTTP") }
    val apiClient = SyncApiClient(jsonClient(engine), baseUrl = "https://issuer.example/v1")

    runSyncCycle(
      FakeAuthProvider(TokenResult.Failed.Terminal),
      database,
      treeStore(database),
      apiClient,
      DeviceIdentity.ephemeral(),
    ) shouldBe CycleOutcome.PausedNoAuth

    runSyncCycle(
      FakeAuthProvider(TokenResult.SignedOut),
      database,
      treeStore(database),
      apiClient,
      DeviceIdentity.ephemeral(),
    ) shouldBe CycleOutcome.PausedNoAuth
  }

  @Test
  fun aRejectedPushRowHasItsOutboxEntryCleared() = runTest {
    val database = InMemoryDatabaseQueryManager()
    val store = treeStore(database)
    store.addMove(
      from = PositionKey("start"),
      move = "e4",
      to = PositionKey("after-e4"),
      isGood = true,
      fromDepth = 0,
    )
    val rejectBody =
      """{"serverTime":"2026-01-01T00:00:00Z","revision":1,"rejected":[{"kind":"node","id":"start","code":"clock_too_far_ahead","reason":"nope"}]}"""
    val engine = MockEngine { request ->
      if (request.method.value == "GET") jsonResponse(emptyPullBody) else jsonResponse(rejectBody)
    }

    val outcome =
      runSyncCycle(
        FakeAuthProvider(TokenResult.Ok("tok")),
        database,
        store,
        SyncApiClient(jsonClient(engine), baseUrl = "https://issuer.example/v1"),
        DeviceIdentity.ephemeral(),
      )

    outcome shouldBe CycleOutcome.Success
    database.getOutbox() shouldBe emptyList()
  }

  @Test
  fun quotaExceededPushPausesTheCycleWithoutClearingTheOutbox() = runTest {
    val database = InMemoryDatabaseQueryManager()
    val store = treeStore(database)
    store.addMove(
      from = PositionKey("start"),
      move = "e4",
      to = PositionKey("after-e4"),
      isGood = true,
      fromDepth = 0,
    )
    val engine = MockEngine { request ->
      if (request.method.value == "PUT") {
        respond(content = ByteReadChannel(""), status = HttpStatusCode.NoContent)
      } else if (request.method.value == "GET") {
        jsonResponse(emptyPullBody)
      } else {
        respond(
          content = ByteReadChannel("""{"code":"quota_exceeded","message":"too many nodes"}"""),
          status = HttpStatusCode.Forbidden,
          headers = headersOf(HttpHeaders.ContentType, "application/json"),
        )
      }
    }

    val outcome =
      runSyncCycle(
        FakeAuthProvider(TokenResult.Ok("tok")),
        database,
        store,
        SyncApiClient(jsonClient(engine), baseUrl = "https://issuer.example/v1"),
        DeviceIdentity.ephemeral(),
      )

    outcome shouldBe CycleOutcome.QuotaExceeded
    database.getOutbox().shouldNotBeEmpty()
  }

  @Test
  fun rateLimitedPushIsTreatedAsTransient() = runTest {
    val database = InMemoryDatabaseQueryManager()
    val store = treeStore(database)
    store.addMove(
      from = PositionKey("start"),
      move = "e4",
      to = PositionKey("after-e4"),
      isGood = true,
      fromDepth = 0,
    )
    val engine = MockEngine { request ->
      if (request.method.value == "GET") jsonResponse(emptyPullBody)
      else respond(content = "", status = HttpStatusCode.TooManyRequests)
    }

    val outcome =
      runSyncCycle(
        FakeAuthProvider(TokenResult.Ok("tok")),
        database,
        store,
        SyncApiClient(jsonClient(engine), baseUrl = "https://issuer.example/v1"),
        DeviceIdentity.ephemeral(),
      )

    outcome shouldBe CycleOutcome.Transient
  }

  @Test
  fun rateLimitedPullIsTreatedAsTransient() = runTest {
    val database = InMemoryDatabaseQueryManager()
    val engine = MockEngine { _ -> respond(content = "", status = HttpStatusCode.TooManyRequests) }

    val outcome =
      runSyncCycle(
        FakeAuthProvider(TokenResult.Ok("tok")),
        database,
        treeStore(database),
        SyncApiClient(jsonClient(engine), baseUrl = "https://issuer.example/v1"),
        DeviceIdentity.ephemeral(),
      )

    outcome shouldBe CycleOutcome.Transient
  }

  @Test
  fun aMultiPagePullDrainsEveryPageAndStopsOnTheEmptyOne() = runTest {
    val database = InMemoryDatabaseQueryManager()
    var pullCalls = 0
    val firstPageBody =
      """{"serverTime":"2026-01-01T00:00:00Z","nextCursor":7,"pageToken":"tok-page-1","nodes":[{"positionKey":"a","dueDate":"2026-01-01T00:00:00Z","lastReview":null,"firstReview":null,"stability":0.0,"difficulty":0.0,"reps":0,"lapses":0,"phase":"NEW","step":0,"isDeleted":false,"updatedAt":"2026-01-01T00:00:00Z","originDevice":"remote","deviceSeq":1}],"edges":[],"settings":[]}"""
    val engine = MockEngine { request ->
      when (request.method.value) {
        "PUT" -> respond(content = ByteReadChannel(""), status = HttpStatusCode.NoContent)
        "GET" -> {
          pullCalls++
          jsonResponse(if (pullCalls == 1) firstPageBody else emptyPullBody)
        }
        else -> jsonResponse(emptyPushBody)
      }
    }

    val outcome =
      runSyncCycle(
        FakeAuthProvider(TokenResult.Ok("tok")),
        database,
        treeStore(database),
        SyncApiClient(jsonClient(engine), baseUrl = "https://issuer.example/v1"),
        DeviceIdentity.ephemeral(),
      )

    outcome shouldBe CycleOutcome.Success
    pullCalls shouldBe 2 // the second page came back empty, which confirmed the first
    database.getPosition(PositionKey("a")).shouldNotBeNull()
  }

  @Test
  fun pushCycleIncludesADirtyRepertoireAndADirtyTagInTheRequest() = runTest {
    val database = InMemoryDatabaseQueryManager()
    val store = treeStore(database)
    store.registerRepertoire("italian-game", "Italian Game", RepertoireColor.WHITE)
    val origin = PositionKey.START_POSITION
    val destination = PositionKey("posA b K")
    store.addMove(from = origin, move = "e4", to = destination, isGood = true, fromDepth = 0)
    store.tagEdge(origin, destination, "italian-game")
    var pushBody: String? = null
    val engine = MockEngine { request ->
      if (request.method.value == "PUT") {
        respond(content = ByteReadChannel(""), status = HttpStatusCode.NoContent)
      } else if (request.method.value == "GET") {
        jsonResponse(emptyPullBody)
      } else {
        pushBody = (request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()
        jsonResponse(emptyPushBody)
      }
    }

    val outcome =
      runSyncCycle(
        FakeAuthProvider(TokenResult.Ok("tok")),
        database,
        store,
        SyncApiClient(jsonClient(engine), baseUrl = "https://issuer.example/v1"),
        DeviceIdentity.ephemeral(),
      )

    outcome shouldBe CycleOutcome.Success
    val sentRequest = SYNC_JSON.decodeFromString<SyncPushRequest>(pushBody!!)
    sentRequest.repertoires.map { it.id } shouldBe listOf("italian-game")
    sentRequest.tags.map { it.repertoireId } shouldBe listOf("italian-game")
  }

  @Test
  fun theCycleNeverPushesBeforeRegistrationSucceeds() = runTest {
    val database = InMemoryDatabaseQueryManager()
    val methods = mutableListOf<String>()
    val engine = MockEngine { request ->
      methods += request.method.value
      respond(
        content = ByteReadChannel("""{"code":"internal","message":"nope"}"""),
        status = HttpStatusCode.InternalServerError,
        headers = headersOf(HttpHeaders.ContentType, "application/json"),
      )
    }

    val outcome =
      runSyncCycle(
        FakeAuthProvider(TokenResult.Ok("tok")),
        database,
        treeStore(database),
        SyncApiClient(jsonClient(engine), baseUrl = "https://issuer.example/v1"),
        DeviceIdentity.ephemeral(),
      )

    outcome shouldBe CycleOutcome.Transient
    methods shouldBe listOf("PUT")
  }

  @Test
  fun theFirstPullOfACycleSendsNoAckAndLaterOnesSendTheLastToken() = runTest {
    val database = InMemoryDatabaseQueryManager()
    val acks = mutableListOf<String?>()
    var pullCalls = 0
    val firstPageBody =
      """{"serverTime":"2026-01-01T00:00:00Z","nextCursor":7,"pageToken":"tok-page-1","nodes":[{"positionKey":"a","dueDate":"2026-01-01T00:00:00Z","lastReview":null,"firstReview":null,"stability":0.0,"difficulty":0.0,"reps":0,"lapses":0,"phase":"NEW","step":0,"isDeleted":false,"updatedAt":"2026-01-01T00:00:00Z","originDevice":"remote","deviceSeq":1}],"edges":[],"settings":[]}"""
    val engine = MockEngine { request ->
      when (request.method.value) {
        "PUT" -> respond(content = ByteReadChannel(""), status = HttpStatusCode.NoContent)
        "GET" -> {
          acks += request.url.parameters["ack"]
          pullCalls++
          jsonResponse(if (pullCalls == 1) firstPageBody else emptyPullBody)
        }
        else -> jsonResponse(emptyPushBody)
      }
    }

    runSyncCycle(
      FakeAuthProvider(TokenResult.Ok("tok")),
      database,
      treeStore(database),
      SyncApiClient(jsonClient(engine), baseUrl = "https://issuer.example/v1"),
      DeviceIdentity.ephemeral(),
    )

    acks shouldBe listOf(null, "tok-page-1")
  }

  @Test
  fun aResyncFromRegisterWipesTheSyncedRowsAndReportsTheWipe() = runTest {
    val database = InMemoryDatabaseQueryManager()
    val store = treeStore(database)
    store.addMove(
      from = PositionKey("start"),
      move = "e4",
      to = PositionKey("after-e4"),
      isGood = true,
      fromDepth = 0,
    )
    database.getPosition(PositionKey("after-e4")).shouldNotBeNull()
    val resetFlags = mutableListOf<Boolean>()
    val engine = MockEngine { request ->
      if (request.method.value == "PUT") {
        val body = (request.body as io.ktor.http.content.TextContent).text
        val reported = body.contains(""""afterReset":true""")
        resetFlags += reported
        if (reported) {
          respond(content = ByteReadChannel(""), status = HttpStatusCode.NoContent)
        } else {
          respond(
            content = ByteReadChannel("""{"code":"resync_required","message":"start over"}"""),
            status = HttpStatusCode.Gone,
            headers = headersOf(HttpHeaders.ContentType, "application/json"),
          )
        }
      } else {
        jsonResponse(emptyPushBody)
      }
    }

    val outcome =
      runSyncCycle(
        FakeAuthProvider(TokenResult.Ok("tok")),
        database,
        store,
        SyncApiClient(jsonClient(engine), baseUrl = "https://issuer.example/v1"),
        DeviceIdentity.ephemeral(),
      )

    outcome shouldBe CycleOutcome.Transient
    resetFlags shouldBe listOf(false, true)
    database.getPosition(PositionKey("after-e4")) shouldBe null
    database.getOutbox() shouldBe emptyList()
  }

  @Test
  fun aResyncFromPullWipesAndReportsTheWipeToo() = runTest {
    val database = InMemoryDatabaseQueryManager()
    val store = treeStore(database)
    store.addMove(
      from = PositionKey("start"),
      move = "e4",
      to = PositionKey("after-e4"),
      isGood = true,
      fromDepth = 0,
    )
    val resetFlags = mutableListOf<Boolean>()
    val engine = MockEngine { request ->
      when (request.method.value) {
        "PUT" -> {
          val body = (request.body as io.ktor.http.content.TextContent).text
          resetFlags += body.contains(""""afterReset":true""")
          respond(content = ByteReadChannel(""), status = HttpStatusCode.NoContent)
        }
        "GET" ->
          respond(
            content = ByteReadChannel("""{"code":"resync_required","message":"start over"}"""),
            status = HttpStatusCode.Gone,
            headers = headersOf(HttpHeaders.ContentType, "application/json"),
          )
        else -> jsonResponse(emptyPushBody)
      }
    }

    runSyncCycle(
      FakeAuthProvider(TokenResult.Ok("tok")),
      database,
      store,
      SyncApiClient(jsonClient(engine), baseUrl = "https://issuer.example/v1"),
      DeviceIdentity.ephemeral(),
    )

    resetFlags shouldBe listOf(false, true)
    database.getPosition(PositionKey("after-e4")) shouldBe null
  }
}
