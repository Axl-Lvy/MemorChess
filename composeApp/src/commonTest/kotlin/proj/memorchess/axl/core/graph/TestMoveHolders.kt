package proj.memorchess.axl.core.graph

import io.kotest.matchers.shouldBe
import kotlin.test.Test

class TestMoveHolders {

  @Test
  fun ownMarkingWins() {
    effectiveMarking(false, listOf(true)) shouldBe false
  }

  @Test
  fun goodBeatsBad() {
    effectiveMarking(null, listOf(false, true)) shouldBe true
  }

  @Test
  fun badWhenEveryInstallSaysBad() {
    effectiveMarking(null, listOf(false)) shouldBe false
  }

  @Test
  fun noHolder() {
    effectiveMarking(null, emptyList()) shouldBe null
  }

  @Test
  fun ownMarkingAlone() {
    effectiveMarking(true, emptyList()) shouldBe true
  }
}
