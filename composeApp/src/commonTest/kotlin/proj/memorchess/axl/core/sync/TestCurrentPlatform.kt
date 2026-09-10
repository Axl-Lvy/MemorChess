package proj.memorchess.axl.core.sync

import io.kotest.matchers.collections.shouldBeIn
import kotlin.test.Test

class TestCurrentPlatform {

  @Test
  fun theCurrentPlatformIsOneOfTheKnownNames() {
    currentPlatform() shouldBeIn
      listOf(
        DevicePlatform.ANDROID,
        DevicePlatform.IOS,
        DevicePlatform.JVM,
        DevicePlatform.WASM_JS,
      )
  }
}
