package proj.memorchess.axl.core.config

import io.kotest.matchers.longs.shouldBeExactly
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import proj.memorchess.axl.core.sync.DeviceIdentity
import proj.memorchess.axl.test_util.TestSettings

class TestSettingSyncMetadataStore {

  @kotlin.test.Test
  fun `a key with no stamp yet reads back null`() {
    val store = SettingSyncMetadataStore(TestSettings(), DeviceIdentity.ephemeral())

    store.read("appTheme") shouldBe null
  }

  @kotlin.test.Test
  fun `stamping a key makes it readable with isDeleted false by default`() = runTest {
    val identity = DeviceIdentity.ephemeral()
    val store = SettingSyncMetadataStore(TestSettings(), identity)

    store.stamp("appTheme")

    val metadata = store.read("appTheme")!!
    metadata.isDeleted shouldBe false
    metadata.originDevice shouldBe identity.originDevice.value
  }

  @kotlin.test.Test
  fun `stamping a key as deleted is readable as deleted`() = runTest {
    val store = SettingSyncMetadataStore(TestSettings(), DeviceIdentity.ephemeral())

    store.stamp("appTheme", isDeleted = true)

    store.read("appTheme")!!.isDeleted shouldBe true
  }

  @kotlin.test.Test
  fun `each stamp advances deviceSeq`() = runTest {
    val store = SettingSyncMetadataStore(TestSettings(), DeviceIdentity.ephemeral())

    store.stamp("appTheme")
    store.stamp("appTheme")

    store.read("appTheme")!!.deviceSeq shouldBeExactly 2L
  }

  @kotlin.test.Test
  fun `stamp returns the deviceSeq it just allocated and wrote`() = runTest {
    val store = SettingSyncMetadataStore(TestSettings(), DeviceIdentity.ephemeral())

    val returned = store.stamp("appTheme")

    returned shouldBeExactly store.read("appTheme")!!.deviceSeq
  }

  @kotlin.test.Test
  fun `two different keys get independent metadata`() = runTest {
    val store = SettingSyncMetadataStore(TestSettings(), DeviceIdentity.ephemeral())

    store.stamp("appTheme")
    store.stamp("chessBoardColor")
    store.stamp("chessBoardColor")

    store.read("appTheme")!!.deviceSeq shouldBeExactly 1L
    store.read("chessBoardColor")!!.deviceSeq shouldBeExactly 3L
  }
}
