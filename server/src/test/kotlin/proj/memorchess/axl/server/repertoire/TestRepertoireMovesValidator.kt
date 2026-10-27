package proj.memorchess.axl.server.repertoire

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlin.test.Test
import proj.memorchess.axl.core.data.repertoire.RepertoireMove
import proj.memorchess.axl.core.engine.GameEngine

class TestRepertoireMovesValidator {

  private val start = GameEngine().toPositionKey().value

  private fun validate(moves: List<RepertoireMove>, maxMoves: Int = 100, maxPlyDepth: Int = 200) =
    RepertoireMovesValidator.validate(moves, maxMoves, maxPlyDepth)

  @Test
  fun `accepts a branching repertoire and counts its moves`() {
    val moves = repertoire(line("e4", "e5", "Nf3"), line("e4", "c5", "Nf3"))

    val result = validate(moves)

    result.shouldBeInstanceOf<MoveListValidation.Valid>().moveCount shouldBe 5
  }

  @Test
  fun `accepts a line with a cycle`() {
    val result = validate(line("Nf3", "Nf6", "Ng1", "Ng8", "e4"))

    result.shouldBeInstanceOf<MoveListValidation.Valid>().moveCount shouldBe 5
  }

  @Test
  fun `accepts transpositions`() {
    val result = validate(repertoire(line("Nf3", "Nf6", "g3"), line("g3", "Nf6", "Nf3")))

    result.shouldBeInstanceOf<MoveListValidation.Valid>().moveCount shouldBe 6
  }

  @Test
  fun `rejects an empty list`() {
    validate(emptyList()).shouldBeInstanceOf<MoveListValidation.Rejected>()
  }

  @Test
  fun `accepts exactly the move cap and refuses one more`() {
    val moves = line("Nf3", "Nf6", "Ng1", "Ng8")

    validate(moves, maxMoves = 4).shouldBeInstanceOf<MoveListValidation.Valid>()
    validate(moves, maxMoves = 3).shouldBeInstanceOf<MoveListValidation.TooLarge>()
  }

  @Test
  fun `rejects two entries for the same origin and destination`() {
    val e4 = line("e4").single()

    validate(listOf(e4, e4.copy(isGood = false))).shouldBeInstanceOf<MoveListValidation.Rejected>()
  }

  @Test
  fun `rejects two entries for the same origin and move`() {
    val e4 = line("e4").single()
    val other = e4.copy(to = line("d4").single().to)

    validate(listOf(e4, other)).shouldBeInstanceOf<MoveListValidation.Rejected>()
  }

  @Test
  fun `rejects a non parseable from key`() {
    val moves = listOf(RepertoireMove("not a fen", "e4", start, isGood = true))

    validate(moves).shouldBeInstanceOf<MoveListValidation.Rejected>()
  }

  @Test
  fun `rejects an illegal move`() {
    val e4 = line("e4").single()

    validate(listOf(e4.copy(move = "e5"))).shouldBeInstanceOf<MoveListValidation.Rejected>()
  }

  @Test
  fun `rejects unparseable san`() {
    val e4 = line("e4").single()

    validate(listOf(e4.copy(move = "zz9"))).shouldBeInstanceOf<MoveListValidation.Rejected>()
  }

  @Test
  fun `rejects a non canonical san`() {
    val nf3 = line("Nf3").single()

    validate(listOf(nf3.copy(move = "Ngf3"))).shouldBeInstanceOf<MoveListValidation.Rejected>()
  }

  @Test
  fun `rejects a destination that does not match the move`() {
    val e4 = line("e4").single()

    validate(listOf(e4.copy(to = line("d4").single().to)))
      .shouldBeInstanceOf<MoveListValidation.Rejected>()
  }

  @Test
  fun `rejects a move not connected to the starting position`() {
    val detached = line("d4", "d5")[1]

    validate(line("e4") + detached).shouldBeInstanceOf<MoveListValidation.Rejected>()
  }

  @Test
  fun `accepts origins at the ply cap and refuses one deeper`() {
    val moves = line("Nf3", "Nf6", "g3")

    validate(moves, maxPlyDepth = 2).shouldBeInstanceOf<MoveListValidation.Valid>()
    validate(moves, maxPlyDepth = 1).shouldBeInstanceOf<MoveListValidation.TooLarge>()
  }
}
