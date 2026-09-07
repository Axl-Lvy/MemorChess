package proj.memorchess.axl.core.data.repertoire

import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlin.test.Test
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import proj.memorchess.axl.core.sync.ApiError
import proj.memorchess.axl.core.sync.ApiErrorCode
import proj.memorchess.axl.core.sync.SYNC_JSON

class TestRepertoirePublishClient {

  private fun clientAnswering(status: HttpStatusCode, body: String): RepertoirePublishClient {
    val engine = MockEngine { _ ->
      respond(
        content = body,
        status = status,
        headers = headersOf(HttpHeaders.ContentType, "application/json"),
      )
    }
    val httpClient = HttpClient(engine) { install(ContentNegotiation) { json(SYNC_JSON) } }
    return RepertoirePublishClient(httpClient, baseUrl = "https://test.invalid/v1/repertoires")
  }

  @Test
  fun publishReturnsPublishedOnCreated() = runTest {
    val descriptor =
      RepertoireDescriptor(
        id = "italian-game",
        name = "Italian Game",
        color = RepertoireColor.WHITE,
        description = "d",
        moveCount = 2,
        file = "pgn/abc.pgn",
      )
    val client = clientAnswering(HttpStatusCode.Created, SYNC_JSON.encodeToString(descriptor))

    val outcome = client.publish("token", "italian-game", "Italian Game", "d", "white", "1. e4")

    outcome shouldBe PublishOutcome.Published(descriptor)
  }

  @Test
  fun publishMapsRemovedToRemovedOnServerNotForbidden() = runTest {
    val client =
      clientAnswering(
        HttpStatusCode.Forbidden,
        SYNC_JSON.encodeToString(ApiError(ApiErrorCode.REMOVED, "removed")),
      )

    val outcome = client.publish("token", "italian-game", "Italian Game", "d", "white", "1. e4")

    outcome shouldBe PublishOutcome.RemovedOnServer
  }

  @Test
  fun publishMapsForbiddenToForbidden() = runTest {
    val client =
      clientAnswering(
        HttpStatusCode.Forbidden,
        SYNC_JSON.encodeToString(ApiError(ApiErrorCode.FORBIDDEN, "not yours")),
      )

    val outcome = client.publish("token", "italian-game", "Italian Game", "d", "white", "1. e4")

    outcome shouldBe PublishOutcome.Forbidden
  }

  @Test
  fun publishMapsUnauthorized() = runTest {
    val client = clientAnswering(HttpStatusCode.Unauthorized, "")

    val outcome = client.publish("token", "italian-game", "Italian Game", "d", "white", "1. e4")

    outcome shouldBe PublishOutcome.Unauthorized
  }

  @Test
  fun publishMapsRateLimited() = runTest {
    val client = clientAnswering(HttpStatusCode.TooManyRequests, "")

    val outcome = client.publish("token", "italian-game", "Italian Game", "d", "white", "1. e4")

    outcome shouldBe PublishOutcome.RateLimited
  }

  @Test
  fun removeReturnsRemovedOnNoContent() = runTest {
    val client = clientAnswering(HttpStatusCode.NoContent, "")

    client.remove("token", "italian-game") shouldBe RemoveOutcome.Removed
  }
}
