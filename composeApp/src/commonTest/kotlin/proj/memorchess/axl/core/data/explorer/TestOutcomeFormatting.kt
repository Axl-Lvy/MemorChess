package proj.memorchess.axl.core.data.explorer

import io.kotest.matchers.shouldBe
import kotlin.test.Test

class TestOutcomeFormatting {

  @Test
  fun compactCountBoundaries() {
    val cases =
      listOf(
        0L to "0",
        1L to "1",
        999L to "999",
        1_000L to "1k",
        1_099L to "1k",
        1_100L to "1.1k",
        999_999L to "999.9k",
        1_000_000L to "1M",
        1_200_000L to "1.2M",
        999_999_999L to "999.9M",
        1_000_000_000L to "1B",
        Long.MAX_VALUE to "9223372036.8B",
      )

    cases.forEach { (count, expected) -> count.toCompactCount() shouldBe expected }
  }

  @Test
  fun percentsOfZeroTotalIsNull() {
    OutcomePercents.of(0, 0, 0) shouldBe null
  }

  @Test
  fun percentsOfSingleGame() {
    OutcomePercents.of(1, 0, 0) shouldBe OutcomePercents(100, 0, 0)
  }

  @Test
  fun percentsOfEvenThreeWaySplitSumTo100() {
    OutcomePercents.of(1, 1, 1) shouldBe OutcomePercents(34, 33, 33)
  }

  @Test
  fun percentsOfExactSplit() {
    OutcomePercents.of(38, 31, 31) shouldBe OutcomePercents(38, 31, 31)
  }

  @Test
  fun percentsOfTinyShare() {
    OutcomePercents.of(1000, 0, 1) shouldBe OutcomePercents(100, 0, 0)
  }

  @Test
  fun percentsGiveLeftoverPointToLargestRemainder() {
    OutcomePercents.of(1, 2, 3) shouldBe OutcomePercents(17, 33, 50)
  }

  @Test
  fun percentsDoNotOverflowNearLongMax() {
    val third = Long.MAX_VALUE / 3

    OutcomePercents.of(third, third, third) shouldBe OutcomePercents(34, 33, 33)
  }

  @Test
  fun shareLabelMarksNonZeroBelowOnePercent() {
    shareLabel(count = 1, percent = 0) shouldBe "<1"
    shareLabel(count = 0, percent = 0) shouldBe "0"
    shareLabel(count = 5, percent = 38) shouldBe "38"
  }
}
