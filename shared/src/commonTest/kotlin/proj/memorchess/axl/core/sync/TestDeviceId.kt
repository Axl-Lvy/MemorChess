package proj.memorchess.axl.core.sync

import io.kotest.matchers.shouldBe
import kotlin.test.Test

class TestDeviceId {

  @Test
  fun aCanonicalUuidParses() {
    DeviceId.parseOrNull(CANONICAL) shouldBe DeviceId(CANONICAL)
  }

  @Test
  fun anUppercaseUuidIsRefused() {
    DeviceId.parseOrNull(CANONICAL.uppercase()) shouldBe null
  }

  @Test
  fun aUuidWithoutHyphensIsRefused() {
    DeviceId.parseOrNull(CANONICAL.replace("-", "")) shouldBe null
  }

  @Test
  fun theNilUuidIsRefused() {
    DeviceId.parseOrNull("00000000-0000-0000-0000-000000000000") shouldBe null
  }

  @Test
  fun theEraseOriginIsTheNilUuid() {
    DeviceId.ERASE_ORIGIN.value shouldBe "00000000-0000-0000-0000-000000000000"
  }

  @Test
  fun theLowestNonNilUuidParses() {
    val lowest = "00000000-0000-0000-0000-000000000001"

    DeviceId.parseOrNull(lowest) shouldBe DeviceId(lowest)
  }

  @Test
  fun theHighestUuidParses() {
    val highest = "ffffffff-ffff-ffff-ffff-ffffffffffff"

    DeviceId.parseOrNull(highest) shouldBe DeviceId(highest)
  }

  @Test
  fun anEmptyStringIsRefused() {
    DeviceId.parseOrNull("") shouldBe null
  }

  @Test
  fun garbageIsRefused() {
    DeviceId.parseOrNull("not-a-uuid") shouldBe null
  }

  @Test
  fun aUuidWithTrailingWhitespaceIsRefused() {
    DeviceId.parseOrNull("$CANONICAL ") shouldBe null
  }

  @Test
  fun aRandomIdParses() {
    val random = DeviceId.random()

    DeviceId.parseOrNull(random.value) shouldBe random
  }

  @Test
  fun anEraseRequestEncodesItsDeviceAsTheBareString() {
    val request = SyncEraseRequest(DeviceId(CANONICAL), deviceSeq = 7)

    val encoded = SYNC_JSON.encodeToString(request)

    encoded shouldBe """{"device":"$CANONICAL","deviceSeq":7}"""
    SYNC_JSON.decodeFromString<SyncEraseRequest>(encoded) shouldBe request
  }

  @Test
  fun aPushRequestEncodesItsDeviceAsTheBareString() {
    val request =
      SyncPushRequest(emptyList(), emptyList(), emptyList(), device = DeviceId(CANONICAL))

    val encoded = SYNC_JSON.encodeToString(request)

    encoded shouldBe
      """{"nodes":[],"edges":[],"settings":[],"repertoires":[],"tags":[],"device":"$CANONICAL"}"""
    SYNC_JSON.decodeFromString<SyncPushRequest>(encoded) shouldBe request
  }

  private companion object {
    const val CANONICAL = "0f1e2d3c-4b5a-4968-8776-a5b4c3d2e1f0"
  }
}
