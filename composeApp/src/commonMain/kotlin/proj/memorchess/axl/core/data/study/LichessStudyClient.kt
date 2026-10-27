package proj.memorchess.axl.core.data.study

import co.touchlab.kermit.Logger
import io.ktor.client.HttpClient
import io.ktor.client.plugins.ResponseException
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import proj.memorchess.axl.core.pgn.PgnGame
import proj.memorchess.axl.core.pgn.PgnParseException
import proj.memorchess.axl.core.pgn.PgnParser

/**
 * Downloads a public Lichess study as parsed PGN games. A private study answers HTTP 404 and is
 * reported as [LichessStudyResult.NotFound].
 */
class LichessStudyClient(private val httpClient: HttpClient) {

  /** Downloads and parses the study referenced by [input], a study URL or a bare study id. */
  suspend fun fetchStudy(input: String): LichessStudyResult {
    val studyId = parseStudyId(input) ?: return LichessStudyResult.InvalidUrl
    val url = "$BASE_URL/$studyId.pgn"
    return try {
      val response = httpClient.get(url) { header(HttpHeaders.UserAgent, USER_AGENT) }
      when {
        response.status.isSuccess() -> parsePgn(response.bodyAsText())
        response.status == HttpStatusCode.NotFound -> LichessStudyResult.NotFound
        else -> {
          LOGGER.w { "Lichess study export returned ${response.status} for $url" }
          LichessStudyResult.HttpError(response.status.value)
        }
      }
    } catch (e: ResponseException) {
      when (e.response.status) {
        HttpStatusCode.NotFound -> LichessStudyResult.NotFound
        else -> {
          LOGGER.w(e) { "Lichess study export failed for $url" }
          LichessStudyResult.HttpError(e.response.status.value)
        }
      }
    } catch (e: Exception) {
      LOGGER.w(e) { "Lichess study request failed for $url" }
      LichessStudyResult.NetworkError(e.message ?: "Request failed")
    }
  }

  /** Parses the PGN export, mapping parse failures to [LichessStudyResult.MalformedPgn]. */
  private fun parsePgn(pgn: String): LichessStudyResult =
    try {
      LichessStudyResult.Ok(PgnParser.parse(pgn))
    } catch (e: PgnParseException) {
      LOGGER.w(e) { "Lichess study export is not valid PGN" }
      LichessStudyResult.MalformedPgn(e.message ?: "Malformed PGN")
    }

  /** Extracts the study id from a bare id or a study URL, or `null` when [input] is neither. */
  private fun parseStudyId(input: String): String? {
    val trimmed = input.trim()
    if (STUDY_ID_REGEX.matches(trimmed)) {
      return trimmed
    }
    return STUDY_URL_REGEX.matchEntire(trimmed)?.groupValues?.get(1)
  }

  companion object {
    private const val BASE_URL = "https://lichess.org/api/study"

    /** Lichess asks every API client to set a descriptive User-Agent. */
    private const val USER_AGENT = "MemorChess (https://github.com/Axl-Lvy/MemorChess)"

    private val STUDY_ID_REGEX = Regex("^[a-zA-Z0-9]{8}$")

    private val STUDY_URL_REGEX =
      Regex(
        "^(?:https?://)?(?:www\\.)?lichess\\.org/study/([a-zA-Z0-9]{8})" +
          "(?:/[a-zA-Z0-9]{8})?/?(?:[?#].*)?$"
      )
  }
}

/** Result of a [LichessStudyClient.fetchStudy] call. */
sealed class LichessStudyResult {

  /** The study was downloaded and parsed. One [PgnGame] per chapter. */
  data class Ok(val games: List<PgnGame>) : LichessStudyResult()

  /** Any failure of [LichessStudyClient.fetchStudy]. */
  sealed class Error : LichessStudyResult()

  /** The input is neither a study URL nor a bare study id. */
  data object InvalidUrl : Error()

  /** Lichess answered HTTP 404: the study does not exist or is private. */
  data object NotFound : Error()

  /** Lichess answered an unexpected HTTP status. */
  data class HttpError(val status: Int) : Error()

  /** The request itself failed, typically because the device is offline. */
  data class NetworkError(val message: String) : Error()

  /** The downloaded export could not be parsed as PGN. */
  data class MalformedPgn(val message: String) : Error()
}

private val LOGGER = Logger.withTag("LichessStudyClient")
