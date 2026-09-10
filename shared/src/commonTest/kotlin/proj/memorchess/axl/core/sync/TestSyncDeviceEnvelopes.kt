package proj.memorchess.axl.core.sync

import io.kotest.matchers.shouldBe
import kotlin.test.Test

class TestSyncDeviceEnvelopes {

  @Test
  fun aRegisterRequestDefaultsAfterResetToFalse() {
    val decoded = SYNC_JSON.decodeFromString<SyncDeviceRegisterRequest>("""{"platform":"jvm"}""")

    decoded.platform shouldBe DevicePlatform.JVM
    decoded.afterReset shouldBe false
  }

  @Test
  fun aPlatformThisBuildHasNeverHeardOfStillDecodes() {
    val decoded = SYNC_JSON.decodeFromString<SyncDeviceRegisterRequest>("""{"platform":"fridge"}""")

    decoded.platform shouldBe "fridge"
  }

  @Test
  fun aStatusResponseRoundTrips() {
    val encoded = SYNC_JSON.encodeToString(SyncDeviceStatusResponse(synced = true))

    SYNC_JSON.decodeFromString<SyncDeviceStatusResponse>(encoded).synced shouldBe true
  }

  @Test
  fun aPullResponseCarriesItsPageToken() {
    val json =
      """{"serverTime":"1970-01-01T00:00:01Z","nextCursor":null,"pageToken":"tok-1",
         "nodes":[],"edges":[],"settings":[]}"""

    SYNC_JSON.decodeFromString<SyncPullResponse>(json).pageToken shouldBe "tok-1"
  }

  @Test
  fun aPushRequestCarriesItsDevice() {
    val request =
      SyncPushRequest(
        nodes = emptyList(),
        edges = emptyList(),
        settings = emptyList(),
        device = "device-1",
      )

    SYNC_JSON.decodeFromString<SyncPushRequest>(SYNC_JSON.encodeToString(request)).device shouldBe
      "device-1"
  }
}
