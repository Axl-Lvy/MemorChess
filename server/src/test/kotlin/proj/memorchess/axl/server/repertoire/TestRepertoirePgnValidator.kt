package proj.memorchess.axl.server.repertoire

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlin.test.Test

class TestRepertoirePgnValidator {

  private val validPgn =
    "[Event \"Test\"]\n[Result \"*\"]\n\n1. e4 e5 (1... c5 2. Nf3 d6) 2. Nf3 Nc6 3. Bb5 *"

  private val linearPgn = "[Event \"E\"]\n[Result \"*\"]\n\n1. e4 e5 2. Nf3 *"

  @Test
  fun `accepts a well formed repertoire and counts its distinct moves`() {
    val result = RepertoirePgnValidator.validate(validPgn, maxPayloadBytes = 10_000, maxMoves = 100)

    result.shouldBeInstanceOf<RepertoireValidation.Valid>()
    // e4, e5, c5 (alt. to e5), Nf3 (after e4 e5), Nc6, Bb5, Nf3 (after e4 c5), d6: 8 distinct
    // (position, san) edges.
    result.moveCount shouldBe 8
  }

  @Test
  fun `rejects a document that does not parse`() {
    val result =
      RepertoirePgnValidator.validate("1. e4 (1... e5", maxPayloadBytes = 10_000, maxMoves = 100)

    result.shouldBeInstanceOf<RepertoireValidation.Rejected>()
  }

  @Test
  fun `rejects a document with no playable move`() {
    val result =
      RepertoirePgnValidator.validate(
        "[Event \"Empty\"]\n[Result \"*\"]\n\n*",
        maxPayloadBytes = 10_000,
        maxMoves = 100,
      )

    result.shouldBeInstanceOf<RepertoireValidation.Rejected>()
  }

  @Test
  fun `rejects an empty payload as having no moves`() {
    val result = RepertoirePgnValidator.validate("", maxPayloadBytes = 10_000, maxMoves = 100)

    result.shouldBeInstanceOf<RepertoireValidation.Rejected>()
  }

  @Test
  fun `rejects a document with an illegal move`() {
    val illegal = "[Event \"Test\"]\n[Result \"*\"]\n\n1. e4 e5 2. Ke2 Ke7 3. Qh5 Qh4 4. Bxb5 *"

    val result = RepertoirePgnValidator.validate(illegal, maxPayloadBytes = 10_000, maxMoves = 100)

    result.shouldBeInstanceOf<RepertoireValidation.Rejected>()
  }

  @Test
  fun `accepts a payload of exactly the byte cap`() {
    val cap = linearPgn.encodeToByteArray().size

    val result = RepertoirePgnValidator.validate(linearPgn, maxPayloadBytes = cap, maxMoves = 100)

    result.shouldBeInstanceOf<RepertoireValidation.Valid>()
  }

  @Test
  fun `rejects a payload one byte over the cap`() {
    val cap = linearPgn.encodeToByteArray().size - 1

    val result = RepertoirePgnValidator.validate(linearPgn, maxPayloadBytes = cap, maxMoves = 100)

    result.shouldBeInstanceOf<RepertoireValidation.TooLarge>()
  }

  @Test
  fun `accepts a repertoire with exactly the move cap`() {
    // linearPgn has 3 distinct moves: e4, e5, Nf3.
    val result = RepertoirePgnValidator.validate(linearPgn, maxPayloadBytes = 10_000, maxMoves = 3)

    result.shouldBeInstanceOf<RepertoireValidation.Valid>()
    result.moveCount shouldBe 3
  }

  @Test
  fun `rejects a repertoire one move over the cap`() {
    val result = RepertoirePgnValidator.validate(linearPgn, maxPayloadBytes = 10_000, maxMoves = 2)

    result.shouldBeInstanceOf<RepertoireValidation.TooLarge>()
  }

  @Test
  fun `rejects a repertoire of zero allowed moves`() {
    val result = RepertoirePgnValidator.validate(linearPgn, maxPayloadBytes = 10_000, maxMoves = 0)

    result.shouldBeInstanceOf<RepertoireValidation.TooLarge>()
  }
}
