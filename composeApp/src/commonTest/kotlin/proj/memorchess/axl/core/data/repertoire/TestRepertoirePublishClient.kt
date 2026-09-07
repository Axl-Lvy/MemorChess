package proj.memorchess.axl.core.data.repertoire

import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.beInstanceOf
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

  private fun throwingClient(): RepertoirePublishClient {
    val engine = MockEngine { throw IllegalStateException("network down") }
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
  fun publishMapsPayloadTooLarge() = runTest {
    val client =
      clientAnswering(
        HttpStatusCode.PayloadTooLarge,
        SYNC_JSON.encodeToString(ApiError(ApiErrorCode.TOO_LARGE, "too big")),
      )

    val outcome = client.publish("token", "italian-game", "Italian Game", "d", "white", "1. e4")

    outcome shouldBe PublishOutcome.PayloadTooLarge("too big")
  }

  @Test
  fun publishMapsInvalidPayload() = runTest {
    val client =
      clientAnswering(
        HttpStatusCode.BadRequest,
        SYNC_JSON.encodeToString(ApiError(ApiErrorCode.INVALID_PGN, "bad pgn")),
      )

    val outcome = client.publish("token", "italian-game", "Italian Game", "d", "white", "1. e4")

    outcome shouldBe PublishOutcome.InvalidPayload("bad pgn")
  }

  @Test
  fun publishMapsQuotaExceeded() = runTest {
    val client =
      clientAnswering(
        HttpStatusCode.Forbidden,
        SYNC_JSON.encodeToString(ApiError(ApiErrorCode.QUOTA_EXCEEDED, "too many")),
      )

    val outcome = client.publish("token", "italian-game", "Italian Game", "d", "white", "1. e4")

    outcome shouldBe PublishOutcome.QuotaExceeded("too many")
  }

  @Test
  fun publishMapsAnUnhandledStatusToFailedWithTheBodyMessage() = runTest {
    val client =
      clientAnswering(
        HttpStatusCode.InternalServerError,
        SYNC_JSON.encodeToString(ApiError(ApiErrorCode.INTERNAL, "server exploded")),
      )

    val outcome = client.publish("token", "italian-game", "Italian Game", "d", "white", "1. e4")

    outcome shouldBe PublishOutcome.Failed("server exploded")
  }

  @Test
  fun publishFallsBackToAGenericMessageWhenTheBodyDoesNotDecodeAsApiError() = runTest {
    val client = clientAnswering(HttpStatusCode.InternalServerError, "not json")

    val outcome = client.publish("token", "italian-game", "Italian Game", "d", "white", "1. e4")

    outcome shouldBe PublishOutcome.Failed("HTTP 500")
  }

  @Test
  fun publishCatchesAThrownExceptionAsFailed() = runTest {
    val client = throwingClient()

    val outcome = client.publish("token", "italian-game", "Italian Game", "d", "white", "1. e4")

    outcome should beInstanceOf<PublishOutcome.Failed>()
  }

  @Test
  fun removeReturnsRemovedOnNoContent() = runTest {
    val client = clientAnswering(HttpStatusCode.NoContent, "")

    client.remove("token", "italian-game") shouldBe RemoveOutcome.Removed
  }

  @Test
  fun removeMapsNotFound() = runTest {
    val client = clientAnswering(HttpStatusCode.NotFound, "")

    client.remove("token", "italian-game") shouldBe RemoveOutcome.NotFound
  }

  @Test
  fun removeMapsUnauthorized() = runTest {
    val client = clientAnswering(HttpStatusCode.Unauthorized, "")

    client.remove("token", "italian-game") shouldBe RemoveOutcome.Unauthorized
  }

  @Test
  fun removeMapsRateLimited() = runTest {
    val client = clientAnswering(HttpStatusCode.TooManyRequests, "")

    client.remove("token", "italian-game") shouldBe RemoveOutcome.RateLimited
  }

  @Test
  fun removeMapsForbidden() = runTest {
    val client = clientAnswering(HttpStatusCode.Forbidden, "")

    client.remove("token", "italian-game") shouldBe RemoveOutcome.Forbidden
  }

  @Test
  fun removeMapsAnUnhandledStatusToFailed() = runTest {
    val client =
      clientAnswering(
        HttpStatusCode.InternalServerError,
        SYNC_JSON.encodeToString(ApiError(ApiErrorCode.INTERNAL, "server exploded")),
      )

    client.remove("token", "italian-game") shouldBe RemoveOutcome.Failed("server exploded")
  }

  @Test
  fun removeCatchesAThrownExceptionAsFailed() = runTest {
    val client = throwingClient()

    val outcome = client.remove("token", "italian-game")

    outcome should beInstanceOf<RemoveOutcome.Failed>()
  }
}
