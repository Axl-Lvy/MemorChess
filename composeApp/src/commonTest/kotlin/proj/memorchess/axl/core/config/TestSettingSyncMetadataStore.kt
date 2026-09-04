package proj.memorchess.axl.core.config

import io.kotest.matchers.longs.shouldBeExactly
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import proj.memorchess.axl.core.sync.DeviceIdentity
import proj.memorchess.axl.test_util.TestSettings

class TestSettingSyncMetadataStore {

  @kotlin.test.Test
  fun `a key with no stamp yet reads back null`() {
    // Arrange & Act.
    val store = SettingSyncMetadataStore(TestSettings(), DeviceIdentity.ephemeral())

    // Assert.
    store.read("appTheme") shouldBe null
  }

  @kotlin.test.Test
  fun `stamping a key makes it readable with isDeleted false by default`() = runTest {
    // Arrange.
    val identity = DeviceIdentity.ephemeral()
    val store = SettingSyncMetadataStore(TestSettings(), identity)

    // Act.
    store.stamp("appTheme")

    // Assert.
    val metadata = store.read("appTheme")!!
    metadata.isDeleted shouldBe false
    metadata.originDevice shouldBe identity.originDevice
  }

  @kotlin.test.Test
  fun `stamping a key as deleted is readable as deleted`() = runTest {
    // Arrange.
    val store = SettingSyncMetadataStore(TestSettings(), DeviceIdentity.ephemeral())

    // Act.
    store.stamp("appTheme", isDeleted = true)

    // Assert.
    store.read("appTheme")!!.isDeleted shouldBe true
  }

  @kotlin.test.Test
  fun `each stamp advances deviceSeq`() = runTest {
    // Arrange.
    val store = SettingSyncMetadataStore(TestSettings(), DeviceIdentity.ephemeral())

    // Act.
    store.stamp("appTheme")
    store.stamp("appTheme")

    // Assert.
    store.read("appTheme")!!.deviceSeq shouldBeExactly 2L
  }

  @kotlin.test.Test
  fun `stamp returns the deviceSeq it just allocated and wrote`() = runTest {
    // Arrange.
    val store = SettingSyncMetadataStore(TestSettings(), DeviceIdentity.ephemeral())

    // Act.
    val returned = store.stamp("appTheme")

    // Assert.
    returned shouldBeExactly store.read("appTheme")!!.deviceSeq
  }

  @kotlin.test.Test
  fun `two different keys get independent metadata`() = runTest {
    // Arrange.
    val store = SettingSyncMetadataStore(TestSettings(), DeviceIdentity.ephemeral())

    // Act.
    store.stamp("appTheme")
    store.stamp("chessBoardColor")
    store.stamp("chessBoardColor")

    // Assert.
    store.read("appTheme")!!.deviceSeq shouldBeExactly 1L
    store.read("chessBoardColor")!!.deviceSeq shouldBeExactly 3L
  }
}
