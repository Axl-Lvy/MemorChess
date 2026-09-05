package proj.memorchess.axl.core.data.repertoire

import io.kotest.matchers.shouldBe
import kotlin.test.Test

class TestRepertoireMastery {

  @Test
  fun solidPercentIsZeroWhenTotalIsZero() {
    val mastery = RepertoireMastery(repertoireName = "Empty", solidCount = 0, totalCount = 0)

    mastery.solidPercent shouldBe 0
  }

  @Test
  fun solidPercentIsZeroAtTheLowestNonZeroTotal() {
    val mastery = RepertoireMastery(repertoireName = "Fresh", solidCount = 0, totalCount = 1)

    mastery.solidPercent shouldBe 0
  }

  @Test
  fun solidPercentIsHundredWhenAllPositionsAreSolid() {
    val mastery = RepertoireMastery(repertoireName = "Mastered", solidCount = 1, totalCount = 1)

    mastery.solidPercent shouldBe 100
  }

  @Test
  fun solidPercentRoundsDownBelowAWholeNumberBoundary() {
    val mastery = RepertoireMastery(repertoireName = "Boundary", solidCount = 1, totalCount = 3)

    mastery.solidPercent shouldBe 33
  }

  @Test
  fun solidPercentRoundsUpAboveAWholeNumberBoundary() {
    val mastery = RepertoireMastery(repertoireName = "Boundary", solidCount = 2, totalCount = 3)

    mastery.solidPercent shouldBe 67
  }

  @Test
  fun solidPercentRoundsAHalfPercentUpToTheNextWholeNumber() {
    val mastery = RepertoireMastery(repertoireName = "Almost", solidCount = 199, totalCount = 200)

    mastery.solidPercent shouldBe 100
  }

  @Test
  fun solidPercentOnARepresentativeLargeValue() {
    val mastery =
      RepertoireMastery(repertoireName = "Italian Game", solidCount = 46, totalCount = 68)

    mastery.solidPercent shouldBe 68
  }

  @Test
  fun placeholderReturnsAFixedShapedSnapshot() {
    val mastery = placeholderRepertoireMastery()

    mastery.repertoireName shouldBe "Italian Game"
    mastery.solidCount shouldBe 46
    mastery.totalCount shouldBe 68
    mastery.solidPercent shouldBe 68
  }
}
