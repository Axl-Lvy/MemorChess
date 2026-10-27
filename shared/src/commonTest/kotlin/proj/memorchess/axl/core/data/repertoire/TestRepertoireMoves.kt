package proj.memorchess.axl.core.data.repertoire

import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlinx.serialization.json.Json
import proj.memorchess.axl.core.engine.GameEngine

class TestRepertoireMoves {

  private val start = GameEngine().toPositionKey().value

  @Test
  fun depthsOfALineAreItsPlyIndex() {
    val moves = line("e4", "e5", "Nf3")

    val depths = moves.depthsFromStart()

    depths shouldBe mapOf(start to 0, moves[0].to to 1, moves[1].to to 2, moves[2].to to 3)
  }

  @Test
  fun transpositionTakesTheShortestDepth() {
    val direct = line("Nf3", "Nf6", "g3")
    val longer = line("g3", "Nf6", "Nf3")
    val viaKnightDance = line("Nf3", "Nc6", "Ng1", "Nb8", "g3", "Nf6", "Nf3")

    val depths = (direct + longer + viaKnightDance).distinct().depthsFromStart()

    depths[direct.last().to] shouldBe 3
  }

  @Test
  fun cycleBackToTheStartTerminatesWithStartAtZero() {
    val moves = line("Nf3", "Nf6", "Ng1", "Ng8")

    val depths = moves.depthsFromStart()

    moves.last().to shouldBe start
    depths[start] shouldBe 0
    depths.size shouldBe 4
  }

  @Test
  fun moveFromAnUnreachableOriginIsAbsent() {
    val afterE4 = line("e4").single().to
    val detached = line("d4", "d5")[1]

    val depths = (line("e4") + detached).depthsFromStart()

    depths shouldBe mapOf(start to 0, afterE4 to 1)
  }

  @Test
  fun emptyListGivesNoDepths() {
    emptyList<RepertoireMove>().depthsFromStart().shouldBeEmpty()
  }

  @Test
  fun requestRoundTripsThroughJson() {
    val request =
      PublishRepertoireRequest(
        id = "italian",
        title = "Italian",
        description = "A classic",
        side = "white",
        moves = line("e4"),
      )

    val decoded = Json.decodeFromString<PublishRepertoireRequest>(Json.encodeToString(request))

    decoded shouldBe request
  }

  /** Plays [sans] from the starting position, every move marked good. */
  private fun line(vararg sans: String): List<RepertoireMove> {
    val engine = GameEngine()
    return sans.map { san ->
      val from = engine.toPositionKey().value
      val played = engine.playSanMove(san)
      RepertoireMove(from = from, move = played, to = engine.toPositionKey().value, isGood = true)
    }
  }
}
