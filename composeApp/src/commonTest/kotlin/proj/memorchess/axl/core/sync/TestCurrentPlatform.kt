package proj.memorchess.axl.core.sync

import io.kotest.matchers.shouldBe
import kotlin.test.Test

class TestCurrentPlatform {

  @Test
  fun thisBuildsPlatformRoundTripsThroughItsWireName() {
    val platform = currentPlatform()

    DevicePlatform.fromWire(platform.wireName) shouldBe platform
  }
}
