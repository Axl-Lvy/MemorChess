package proj.memorchess.axl.core.scheduling

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class TestRetrievability {

  @Test
  fun zeroIsZeroPercent() {
    Retrievability(0.0).toPercent() shouldBe 0
  }

  @Test
  fun justBelowHalfAPercentRoundsDown() {
    Retrievability(0.004).toPercent() shouldBe 0
  }

  @Test
  fun halfAPercentRoundsUp() {
    Retrievability(0.005).toPercent() shouldBe 1
  }

  @Test
  fun justBelowOneRoundsToAHundred() {
    Retrievability(0.995).toPercent() shouldBe 100
  }

  @Test
  fun oneIsAHundredPercent() {
    Retrievability(1.0).toPercent() shouldBe 100
  }

  @Test
  fun aValueBelowZeroIsRejected() {
    shouldThrow<IllegalArgumentException> { Retrievability(-0.001) }
  }

  @Test
  fun aValueAboveOneIsRejected() {
    shouldThrow<IllegalArgumentException> { Retrievability(1.001) }
  }

  @Test
  fun notANumberIsRejected() {
    shouldThrow<IllegalArgumentException> { Retrievability(Double.NaN) }
  }
}
