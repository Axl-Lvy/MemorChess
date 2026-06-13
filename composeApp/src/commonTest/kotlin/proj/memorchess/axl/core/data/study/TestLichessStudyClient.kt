package proj.memorchess.axl.core.data.study

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.ByteReadChannel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class TestLichessStudyClient {

  private val singleChapterPgn =
    """
    [Event "My Study: Chapter 1"]
    [Site "https://lichess.org/study/abcd1234"]
    [Result "*"]

    1. e4 e5 2. Nf3 Nc6 *
    """
      .trimIndent()

  private val multiChapterPgn =
    """
    [Event "My Study: Chapter 1"]

    1. e4 e5 *

    [Event "My Study: Chapter 2"]

    1. d4 d5 *
    """
      .trimIndent()

  private fun engineRespondingPgn(pgn: String): MockEngine = MockEngine {
    respond(content = ByteReadChannel(pgn), status = HttpStatusCode.OK)
  }

  @Test
  fun studyUrlVariantsAllRequestTheSameExportEndpoint() = runTest {
    // Arrange
    val acceptedInputs =
      listOf(
        "https://lichess.org/study/abcd1234",
        "https://lichess.org/study/abcd1234/",
        "https://lichess.org/study/abcd1234/efgh5678",
        "https://lichess.org/study/abcd1234?source=share",
        "https://www.lichess.org/study/abcd1234",
        "http://lichess.org/study/abcd1234",
        "lichess.org/study/abcd1234",
        "abcd1234",
        "  abcd1234  ",
      )

    for (input in acceptedInputs) {
      val engine = engineRespondingPgn(singleChapterPgn)
      val client = LichessStudyClient(HttpClient(engine))

      // Act
      val result = client.fetchStudy(input)

      // Assert
      assertIs<LichessStudyResult.Ok>(result, "input: $input")
      assertEquals(
        "https://lichess.org/api/study/abcd1234.pgn",
        engine.requestHistory.single().url.toString(),
        "input: $input",
      )
    }
  }

  @Test
  fun invalidInputsAreRejectedWithoutAnyRequest() = runTest {
    // Arrange
    val rejectedInputs =
      listOf(
        "",
        "   ",
        "garbage",
        "abc123",
        "abcd12345",
        "abcd 234",
        "abcd_234",
        "https://lichess.org/abcd1234",
        "https://example.com/study/abcd1234",
        "https://lichess.org/study/abcd1234extra",
        "ftp://lichess.org/study/abcd1234",
      )

    for (input in rejectedInputs) {
      val engine = engineRespondingPgn(singleChapterPgn)
      val client = LichessStudyClient(HttpClient(engine))

      // Act
      val result = client.fetchStudy(input)

      // Assert
      assertEquals(LichessStudyResult.InvalidUrl, result, "input: $input")
      assertTrue(engine.requestHistory.isEmpty(), "input: $input")
    }
  }

  @Test
  fun singleChapterStudyIsParsedAsOneGame() = runTest {
    // Arrange
    val client = LichessStudyClient(HttpClient(engineRespondingPgn(singleChapterPgn)))

    // Act
    val result = client.fetchStudy("abcd1234")

    // Assert
    val games = assertIs<LichessStudyResult.Ok>(result).games
    assertEquals(1, games.size)
    assertEquals("e4", games.single().moves.single().san)
  }

  @Test
  fun multiChapterStudyIsParsedAsOneGamePerChapter() = runTest {
    // Arrange
    val client = LichessStudyClient(HttpClient(engineRespondingPgn(multiChapterPgn)))

    // Act
    val result = client.fetchStudy("abcd1234")

    // Assert
    val games = assertIs<LichessStudyResult.Ok>(result).games
    assertEquals(2, games.size)
    assertEquals(listOf("e4", "d4"), games.map { it.moves.single().san })
  }

  @Test
  fun missingStudyIsReportedAsNotFound() = runTest {
    // Arrange
    val engine = MockEngine { respond(content = "", status = HttpStatusCode.NotFound) }
    val client = LichessStudyClient(HttpClient(engine))

    // Act
    val result = client.fetchStudy("abcd1234")

    // Assert
    assertEquals(LichessStudyResult.NotFound, result)
  }

  @Test
  fun unexpectedStatusIsReportedAsHttpError() = runTest {
    // Arrange
    val engine = MockEngine { respond(content = "", status = HttpStatusCode.InternalServerError) }
    val client = LichessStudyClient(HttpClient(engine))

    // Act
    val result = client.fetchStudy("abcd1234")

    // Assert
    assertEquals(LichessStudyResult.HttpError(500), result)
  }

  @Test
  fun requestFailureIsReportedAsNetworkError() = runTest {
    // Arrange
    val engine = MockEngine { error("Connection refused") }
    val client = LichessStudyClient(HttpClient(engine))

    // Act
    val result = client.fetchStudy("abcd1234")

    // Assert
    assertIs<LichessStudyResult.NetworkError>(result)
  }

  @Test
  fun unparseablePgnIsReportedAsMalformedPgn() = runTest {
    // Arrange
    val client = LichessStudyClient(HttpClient(engineRespondingPgn("1. e4 (e5 *")))

    // Act
    val result = client.fetchStudy("abcd1234")

    // Assert
    assertIs<LichessStudyResult.MalformedPgn>(result)
  }
}
