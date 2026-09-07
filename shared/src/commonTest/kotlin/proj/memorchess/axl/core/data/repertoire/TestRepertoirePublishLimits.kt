package proj.memorchess.axl.core.data.repertoire

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class TestRepertoirePublishLimits {

  @Test
  fun idProblemAcceptsAWellFormedId() {
    RepertoirePublishLimits.idProblem("italian-game").shouldBeNull()
  }

  @Test
  fun idProblemRejectsAnIdShorterThanTheMinimum() {
    RepertoirePublishLimits.idProblem("ab") shouldBe
      "id must be ${RepertoirePublishLimits.MIN_ID_LENGTH} to" +
        " ${RepertoirePublishLimits.MAX_ID_LENGTH} characters, was 2"
  }

  @Test
  fun idProblemRejectsAnIdLongerThanTheMaximum() {
    val id = "a".repeat(RepertoirePublishLimits.MAX_ID_LENGTH + 1)

    RepertoirePublishLimits.idProblem(id) shouldBe
      "id must be ${RepertoirePublishLimits.MIN_ID_LENGTH} to" +
        " ${RepertoirePublishLimits.MAX_ID_LENGTH} characters, was ${id.length}"
  }

  @Test
  fun idProblemRejectsUppercaseCharacters() {
    RepertoirePublishLimits.idProblem("Italian-Game") shouldBe
      "id must be lowercase letters, digits and single hyphens, was 'Italian-Game'"
  }

  @Test
  fun idProblemRejectsConsecutiveHyphens() {
    RepertoirePublishLimits.idProblem("italian--game") shouldBe
      "id must be lowercase letters, digits and single hyphens, was 'italian--game'"
  }

  @Test
  fun idProblemRejectsALeadingHyphen() {
    RepertoirePublishLimits.idProblem("-italian-game") shouldBe
      "id must be lowercase letters, digits and single hyphens, was '-italian-game'"
  }
}
