package proj.memorchess.axl.server.sync

import proj.memorchess.axl.core.sync.DeviceId
import kotlin.time.Instant
import proj.memorchess.axl.core.sync.SyncPullResponse
import proj.memorchess.axl.core.sync.SyncPushRequest
import proj.memorchess.axl.core.sync.SyncPushResponse

/**
 * How a [TestDevice] reaches the server. The identity is the transport's, never the device's, as in
 * production.
 */
internal interface SyncTransport {

  suspend fun push(request: SyncPushRequest, serverNow: Instant): SyncPushResponse

  suspend fun pull(ack: String?, limit: Int, serverNow: Instant): SyncPullResponse
}

/** Calls the store directly, skipping HTTP. */
internal class StoreTransport(
  private val store: SyncStore,
  private val userId: String,
  private val deviceId: DeviceId,
) : SyncTransport {

  override suspend fun push(request: SyncPushRequest, serverNow: Instant) =
    store.push(userId, deviceId, request, serverNow)

  override suspend fun pull(ack: String?, limit: Int, serverNow: Instant) =
    store.pull(userId, deviceId, ack, limit, serverNow)
}
