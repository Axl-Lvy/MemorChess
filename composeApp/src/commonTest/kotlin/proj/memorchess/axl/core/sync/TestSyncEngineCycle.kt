package proj.memorchess.axl.core.sync

import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.ByteReadChannel
import kotlin.test.Test
import kotlin.time.Instant
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
    """{"serverTime":"2026-01-01T00:00:00Z","nextCursor":null,"nodes":[],"edges":[],"settings":[]}"""
  private val emptyPushBody =
    """{"serverTime":"2026-01-01T00:00:00Z","revision":1,"rejected":[]}"""

  private fun MockRequestHandleScope.jsonResponse(body: String) =
    respond(
      content = ByteReadChannel(body),
      status = HttpStatusCode.OK,
      headers = headersOf(HttpHeaders.ContentType, "application/json"),
    )

  private fun emptyPullEngine() = MockEngine { request ->
    if (request.method.value == "GET") jsonResponse(emptyPullBody) else jsonResponse(emptyPushBody)
  }

  private fun treeStore(database: InMemoryDatabaseQueryManager = InMemoryDatabaseQueryManager()) =
    TreeStore(database, CoroutineScope(Dispatchers.Unconfined), DeviceIdentity.ephemeral())

  @Test
  fun emptyCycleSucceedsAndLeavesCursorUntouched() = runTest {
    val database = InMemoryDatabaseQueryManager()
    val cursorStore = SyncCursorStore(proj.memorchess.axl.test_util.TestSettings())

    val outcome =
      runSyncCycle(
        authProvider = FakeAuthProvider(TokenResult.Ok("tok")),
        database = database,
        treeStore = treeStore(database),
        apiClient =
          SyncApiClient(jsonClient(emptyPullEngine()), baseUrl = "https://issuer.example/v1"),
        cursorStore = cursorStore,
      )

    outcome shouldBe CycleOutcome.Success
    cursorStore.read() shouldBe null
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
        cursorStore = SyncCursorStore(proj.memorchess.axl.test_util.TestSettings()),
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
      SyncCursorStore(proj.memorchess.axl.test_util.TestSettings()),
    ) shouldBe CycleOutcome.PausedNoAuth

    runSyncCycle(
      FakeAuthProvider(TokenResult.SignedOut),
      database,
      treeStore(database),
      apiClient,
      SyncCursorStore(proj.memorchess.axl.test_util.TestSettings()),
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
        SyncCursorStore(proj.memorchess.axl.test_util.TestSettings()),
      )

    outcome shouldBe CycleOutcome.Success
    database.getOutbox() shouldBe emptyList()
  }

  @Test
  fun aMultiPagePullDrainsBothPagesAndPersistsTheFinalCursor() = runTest {
    val database = InMemoryDatabaseQueryManager()
    var pullCalls = 0
    val firstPageBody =
      """{"serverTime":"2026-01-01T00:00:00Z","nextCursor":7,"nodes":[{"positionKey":"a","dueDate":"2026-01-01T00:00:00Z","lastReview":null,"firstReview":null,"stability":0.0,"difficulty":0.0,"reps":0,"lapses":0,"phase":"NEW","step":0,"isDeleted":false,"updatedAt":"2026-01-01T00:00:00Z","originDevice":"remote","deviceSeq":1}],"edges":[],"settings":[]}"""
    val engine = MockEngine { request ->
      if (request.method.value == "GET") {
        pullCalls++
        jsonResponse(if (pullCalls == 1) firstPageBody else emptyPullBody)
      } else {
        jsonResponse(emptyPushBody)
      }
    }
    val cursorStore = SyncCursorStore(proj.memorchess.axl.test_util.TestSettings())

    val outcome =
      runSyncCycle(
        FakeAuthProvider(TokenResult.Ok("tok")),
        database,
        treeStore(database),
        SyncApiClient(jsonClient(engine), baseUrl = "https://issuer.example/v1"),
        cursorStore,
      )

    outcome shouldBe CycleOutcome.Success
    pullCalls shouldBe 2
    cursorStore.read() shouldBe null // the final page's nextCursor was null
    database.getPosition(PositionKey("a")).shouldNotBeNull()
  }
}
