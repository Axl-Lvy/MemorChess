package proj.memorchess.axl.core.data.explorer

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlinx.serialization.json.Json

class TestLichessExplorerResponse {

  private val json = Json { ignoreUnknownKeys = true }

  @Test
  fun totalGamesSumsWhiteDrawsBlack() {
    val response =
      LichessExplorerResponse(white = 3, draws = 4, black = 5, moves = emptyList(), opening = null)
    response.totalGames shouldBe 12L
  }

  @Test
  fun moveTotalGamesSumsWhiteDrawsBlack() {
    val move = LichessExplorerMove(uci = "e2e4", san = "e4", white = 2, draws = 3, black = 1)
    move.totalGames shouldBe 6L
  }

  @Test
  fun parsesFullResponseWithOpening() {
    val raw =
      """
      {
        "white": 10, "draws": 5, "black": 3,
        "moves": [{"uci":"e2e4","san":"e4","averageRating":2400,"white":7,"draws":2,"black":1}],
        "opening": {"eco":"B00","name":"King's Pawn"}
      }
      """
        .trimIndent()
    val parsed = json.decodeFromString<LichessExplorerResponse>(raw)
    parsed.totalGames shouldBe 18L
    parsed.opening?.eco shouldBe "B00"
    parsed.moves.first().averageRating shouldBe 2400
  }

  @Test
  fun parsesResponseWithoutOpening() {
    val raw =
      """
      {"white": 1, "draws": 0, "black": 1, "moves": []}
      """
        .trimIndent()
    val parsed = json.decodeFromString<LichessExplorerResponse>(raw)
    parsed.opening.shouldBeNull()
    parsed.moves shouldBe emptyList()
  }

  @Test
  fun parsesMoveWithoutAverageRating() {
    val raw = """{"uci":"d2d4","san":"d4","white":1,"draws":1,"black":1}""".trimIndent()
    val parsed = json.decodeFromString<LichessExplorerMove>(raw)
    parsed.averageRating.shouldBeNull()
    parsed.san shouldBe "d4"
  }
}
