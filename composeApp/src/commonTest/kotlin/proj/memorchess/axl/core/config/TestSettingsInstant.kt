package proj.memorchess.axl.core.config

import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlin.time.Instant
import proj.memorchess.axl.test_util.TestSettings

class TestSettingsInstant {

  @Test
  fun `an absent key reads back null`() {
    TestSettings().getInstantOrNull("missing") shouldBe null
  }

  @Test
  fun `the epoch round trips`() {
    val settings = TestSettings()

    settings.putInstant("at", Instant.fromEpochMilliseconds(0))

    settings.getInstantOrNull("at") shouldBe Instant.fromEpochMilliseconds(0)
  }

  @Test
  fun `one millisecond after the epoch keeps its millisecond`() {
    val settings = TestSettings()

    settings.putInstant("at", Instant.fromEpochMilliseconds(1))

    settings.getInstantOrNull("at") shouldBe Instant.fromEpochMilliseconds(1)
  }

  @Test
  fun `a sub second instant keeps its milliseconds`() {
    val settings = TestSettings()
    val at = Instant.parse("2026-10-27T10:15:30.123Z")

    settings.putInstant("at", at)

    settings.getInstantOrNull("at") shouldBe at
  }

  @Test
  fun `an instant before the epoch round trips`() {
    val settings = TestSettings()

    settings.putInstant("at", Instant.fromEpochMilliseconds(-1))

    settings.getInstantOrNull("at") shouldBe Instant.fromEpochMilliseconds(-1)
  }

  @Test
  fun `a far future instant round trips`() {
    val settings = TestSettings()
    val at = Instant.parse("9999-12-31T23:59:59.999Z")

    settings.putInstant("at", at)

    settings.getInstantOrNull("at") shouldBe at
  }
}
