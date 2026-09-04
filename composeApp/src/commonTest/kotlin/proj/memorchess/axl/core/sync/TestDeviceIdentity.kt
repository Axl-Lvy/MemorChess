package proj.memorchess.axl.core.sync

import io.kotest.matchers.longs.shouldBeExactly
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.test.runTest
import proj.memorchess.axl.test_util.TestSettings

class TestDeviceIdentity {

  @kotlin.test.Test
  fun `ephemeral instances have distinct origin devices`() {
    // Arrange & Act.
    val a = DeviceIdentity.ephemeral()
    val b = DeviceIdentity.ephemeral()

    // Assert.
    a.originDevice shouldNotBe b.originDevice
  }

  @kotlin.test.Test
  fun `the first allocated sequence is exactly one and never zero`() = runTest {
    // Arrange.
    val identity = DeviceIdentity.ephemeral()

    // Act.
    val first = identity.nextDeviceSeq()

    // Assert.
    first shouldBeExactly 1L
  }

  @kotlin.test.Test
  fun `nextDeviceSeq strictly increases`() = runTest {
    // Arrange.
    val identity = DeviceIdentity.ephemeral()

    // Act.
    val first = identity.nextDeviceSeq()
    val second = identity.nextDeviceSeq()
    val third = identity.nextDeviceSeq()

    // Assert.
    first shouldBeExactly 1L
    second shouldBeExactly 2L
    third shouldBeExactly 3L
  }

  @kotlin.test.Test
  fun `persisted origin device survives a fresh instance over the same settings`() {
    // Arrange.
    val settings = TestSettings()
    val first = DeviceIdentity.persisted(settings)
    val originDevice = first.originDevice

    // Act.
    val second = DeviceIdentity.persisted(settings)

    // Assert.
    second.originDevice shouldBe originDevice
  }

  @kotlin.test.Test
  fun `a fresh instance over the same settings never reuses a number already handed out`() =
    runTest {
      // Arrange.
      val settings = TestSettings()
      val first = DeviceIdentity.persisted(settings)
      first.nextDeviceSeq()
      val lastAllocated = first.nextDeviceSeq()

      // Act.
      val second = DeviceIdentity.persisted(settings)

      // Assert: the two allocations above never durably persisted past the reserved block
      // boundary, so a fresh instance resumes strictly above every number the first instance
      // actually handed out, even though most of that block went unused.
      second.nextDeviceSeq() shouldBeGreaterThan lastAllocated
    }

  @kotlin.test.Test
  fun `the reservation boundary is persisted before the first number in a block is handed out`() =
    runTest {
      // Arrange.
      val settings = TestSettings()
      val identity = DeviceIdentity.persisted(settings)

      // Act.
      identity.nextDeviceSeq()

      // Assert: the durable boundary is strictly above the number just handed out, proving the
      // block was reserved ahead of use rather than persisted one at a time.
      settings.getLong("sync.deviceSeq", 0L) shouldBeGreaterThan 1L
    }

  @kotlin.test.Test
  fun `ephemeral deviceSeq is never persisted`() = runTest {
    // Arrange.
    val settings = TestSettings()

    // Act.
    DeviceIdentity.ephemeral().nextDeviceSeq()

    // Assert.
    settings.size shouldBe 0
  }

  @kotlin.test.Test
  fun `a large prior sequence continues strictly increasing after a restart`() = runTest {
    // Arrange.
    val settings = TestSettings()
    settings.putLong("sync.deviceSeq", 1_000_000_000_000L)
    val identity = DeviceIdentity.persisted(settings)

    // Act.
    val next = identity.nextDeviceSeq()

    // Assert.
    next shouldBeExactly 1_000_000_000_001L
  }
}
